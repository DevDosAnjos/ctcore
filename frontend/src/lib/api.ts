export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    public fields: { field: string; message: string }[] = [],
  ) {
    super(code);
  }
}

const cookie = (name: string) =>
  document.cookie
    .split("; ")
    .find((c) => c.startsWith(`${name}=`))
    ?.slice(name.length + 1);

/** Chama `/api/v1`; devolve o JSON ou lança ApiError (status 0 com a rede fora). POST/PUT/PATCH/DELETE levam o token CSRF. FormData segue como está (multipart). */
export async function api<T>(path: string, init: { method?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const method = init.method ?? "GET";
  const headers: Record<string, string> = { ...init.headers };
  const form = init.body instanceof FormData;
  if (init.body !== undefined && !form) headers["Content-Type"] = "application/json";
  if (method !== "GET") {
    const xsrf = cookie("XSRF-TOKEN");
    if (xsrf) headers["X-XSRF-TOKEN"] = decodeURIComponent(xsrf);
  }
  let res: Response;
  try {
    res = await fetch(`/api/v1${path}`, {
      method,
      headers,
      credentials: "same-origin",
      body: init.body === undefined ? undefined : form ? (init.body as FormData) : JSON.stringify(init.body),
    });
  } catch {
    throw new ApiError(0, "NETWORK");
  }
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new ApiError(res.status, err.code ?? "INTERNAL_ERROR", err.fields ?? []);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text(); // 202 sem corpo (ex.: pedido de troca de e-mail) também é sucesso
  return (text ? JSON.parse(text) : undefined) as T;
}
