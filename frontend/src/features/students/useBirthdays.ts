import { useResource } from "./useStudentRecord";
import type { StudentBirthday } from "./types";

export const useBirthdays = () => useResource<StudentBirthday[]>("/students/birthdays");
