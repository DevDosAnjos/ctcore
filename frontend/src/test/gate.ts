/** Segura a resposta de um handler MSW até o teste liberar, para afirmar o estado "Carregando..." sem depender de tempo. */
export function gate() {
  let release!: () => void;
  const opened = new Promise<void>((resolve) => (release = resolve));
  return { opened, release };
}
