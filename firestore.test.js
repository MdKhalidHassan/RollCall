const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_rep";
const BOB_UID = "bob_other_user";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write students or class configs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("class_configs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_01").set({
      userId: ALICE_UID,
      rollNumber: "01",
      name: "Alice",
      phone: "01700000001",
      gmail: "alice@gmail.com",
      present: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, toggle present, update details, and delete students", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const studentsCol = aliceDb.collection("users").doc(ALICE_UID).collection("students");

  // List on empty subcollection must succeed
  await assertSucceeds(studentsCol.get());

  // Create student with comment & photoData mirroring Kotlin payload (no @DocumentId id field)
  await assertSucceeds(
    studentsCol.doc("stu_01").set({
      userId: ALICE_UID,
      rollNumber: "01",
      name: "Nafisa Rahman",
      phone: "01711223344",
      gmail: "nafisa.rahman@gmail.com",
      present: false,
      comment: "Class representative for morning lab",
      photoData: "AAAA",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Toggle present partial update
  await assertSucceeds(
    studentsCol.doc("stu_01").update({
      present: true,
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Edit full student details partial update including comment & photoData
  await assertSucceeds(
    studentsCol.doc("stu_01").update({
      rollNumber: "02",
      name: "Nafisa Rahman",
      phone: "01711223399",
      gmail: "nafisa.updated@gmail.com",
      present: true,
      comment: "Updated note",
      photoData: "BBBB",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject oversized comment (> 500 chars)
  await assertFails(
    studentsCol.doc("stu_01").update({
      rollNumber: "02",
      name: "Nafisa Rahman",
      phone: "01711223399",
      gmail: "nafisa.updated@gmail.com",
      present: true,
      comment: "x".repeat(501),
      photoData: "BBBB",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List populated subcollection
  await assertSucceeds(studentsCol.get());

  // Delete student
  await assertSucceeds(studentsCol.doc("stu_01").delete());
});

test("Owner can create and update class_configs default document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const configDoc = aliceDb.collection("users").doc(ALICE_UID).collection("class_configs").doc("default");

  await assertSucceeds(
    configDoc.set({
      userId: ALICE_UID,
      courseName: "CSE 301: Algorithms",
      department: "Computer Science & Engineering",
      batch: "Batch 24",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    configDoc.update({
      courseName: "CSE 302: Operating Systems",
      department: "CSE",
      batch: "24th",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Reject cross-user access, ghost fields, immutable mutation, future timestamp, and oversized fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const aliceCol = aliceDb.collection("users").doc(ALICE_UID).collection("students");

  await assertSucceeds(
    aliceCol.doc("stu_01").set({
      userId: ALICE_UID,
      rollNumber: "01",
      name: "Tariq Hassan",
      phone: "01811000111",
      gmail: "tariq@gmail.com",
      present: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot read, list, update, or delete Alice's students
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_01").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_01").update({
      present: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_01").delete());

  // Reject ghost `id` field on create
  await assertFails(
    aliceCol.doc("stu_ghost").set({
      id: "stu_ghost",
      userId: ALICE_UID,
      rollNumber: "02",
      name: "Ghost Field",
      phone: "",
      gmail: "",
      present: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt update
  await assertFails(
    aliceCol.doc("stu_01").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceCol.doc("stu_future").set({
      userId: ALICE_UID,
      rollNumber: "03",
      name: "Future Student",
      phone: "",
      gmail: "",
      present: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject blank rollNumber or oversized name
  await assertFails(
    aliceCol.doc("stu_bad").set({
      userId: ALICE_UID,
      rollNumber: "",
      name: "No Roll",
      phone: "",
      gmail: "",
      present: false,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Owner can save, list, and delete class attendance_sessions; others and unauth rejected", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const aliceSessions = aliceDb.collection("users").doc(ALICE_UID).collection("attendance_sessions");

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("attendance_sessions").get());
  await assertSucceeds(aliceSessions.get());

  await assertSucceeds(
    aliceSessions.doc("sess_01").set({
      userId: ALICE_UID,
      dateFormatted: "17-11-2026",
      dateMillis: Date.now() - 5000,
      courseName: "CSE 301: Algorithms",
      department: "CSE",
      batch: "Batch 24",
      presentRolls: ["01", "03", "07"],
      totalStudents: 12,
      formattedOutput: "Date: 17-11-2026\nCourse: CSE 301: Algorithms\nDepartment: CSE\nBatch: Batch 24\nPresent Rolls: 01, 03, 07",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceSessions.get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("attendance_sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("attendance_sessions").doc("sess_01").delete());

  // Reject negative totalStudents
  await assertFails(
    aliceSessions.doc("sess_bad").set({
      userId: ALICE_UID,
      dateFormatted: "17-11-2026",
      dateMillis: Date.now() - 5000,
      courseName: "CSE 301",
      department: "CSE",
      batch: "Batch 24",
      presentRolls: ["01"],
      totalStudents: -1,
      formattedOutput: "invalid",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceSessions.doc("sess_01").delete());
});
