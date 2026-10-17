/** RN-ACE-16: só caminho interno; qualquer outra coisa vira "/". */
export function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : "/";
}
