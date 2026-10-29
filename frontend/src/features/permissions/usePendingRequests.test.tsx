import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { usePendingRequests } from "./usePendingRequests";

function Probe({ enabled = true }: { enabled?: boolean }) {
  return <p data-testid="n">{usePendingRequests(enabled)}</p>;
}

const wait = () => new Promise((r) => setTimeout(r, 50));

describe("usePendingRequests", () => {
  it("CA2: lê o total de pedidos com size=1", async () => {
    let url = "";
    server.use(
      http.get("/api/v1/portal-requests", ({ request }) => {
        url = request.url;
        return HttpResponse.json({ items: [], total: 3 });
      }),
    );
    render(<Probe />);
    expect(await screen.findByText("3")).toBeInTheDocument();
    expect(url).toContain("size=1");
  });

  it("CA2: 403 deixa zero", async () => {
    server.use(http.get("/api/v1/portal-requests", () => HttpResponse.json({ code: "FORBIDDEN" }, { status: 403 })));
    render(<Probe />);
    await wait();
    expect(screen.getByTestId("n")).toHaveTextContent("0");
  });

  it("não consulta quando o item não está no menu", async () => {
    let called = false;
    server.use(
      http.get("/api/v1/portal-requests", () => {
        called = true;
        return HttpResponse.json({ items: [], total: 9 });
      }),
    );
    render(<Probe enabled={false} />);
    await wait();
    expect(called).toBe(false);
    expect(screen.getByTestId("n")).toHaveTextContent("0");
  });
});
