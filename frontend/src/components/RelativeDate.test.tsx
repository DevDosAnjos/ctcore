import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RelativeDate } from "./RelativeDate";

const now = new Date("2026-10-08T15:00:00Z"); // 12h em Brasília
const at = (days: number) => new Date(now.getTime() - days * 86_400_000).toISOString();

describe("RelativeDate (RNF-23)", () => {
  it.each([
    [0, "hoje"],
    [1, "ontem"],
    [3, "há 3 dias"],
    [30, "há 30 dias"],
    [31, "07/09/2026"],
    [-2, "10/10/2026"],
  ])("CA6 CA7 RNF-23: %i dias atrás mostra %s", (d, text) => {
    render(<RelativeDate value={at(d)} now={now} />);
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it("CA6 RNF-23: 23h50 de ontem visto às 00h10 de hoje é ontem", () => {
    render(<RelativeDate value="2026-10-08T02:50:00Z" now={new Date("2026-10-08T03:10:00Z")} />);
    expect(screen.getByText("ontem")).toBeInTheDocument();
  });

  it.each([[""], [null], ["lixo"]])("CA8 RNF-23: valor %s mostra traço", (v) => {
    render(<RelativeDate value={v} now={now} />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("CA10 RNF-23: é um time com ISO dentro de botão", () => {
    render(<RelativeDate value="2026-10-08T22:30:00Z" now={now} />);
    expect(screen.getByRole("button").querySelector("time")).toHaveTextContent("hoje");
  });

  it("CA9 RNF-23 RNF-20: mouse, foco, toque e Esc", async () => {
    const u = userEvent.setup();
    render(<RelativeDate value="2026-10-08T22:30:00Z" now={new Date("2026-10-10T15:00:00Z")} />);
    const b = screen.getByRole("button");
    expect(b.querySelector("time")).toHaveAttribute("datetime", "2026-10-08T22:30:00.000Z");
    expect(screen.queryByRole("tooltip")).toBeNull();
    await u.hover(b);
    expect(screen.getByRole("tooltip")).toHaveTextContent("08/10/2026 19h30");
    expect(b).toHaveAttribute("aria-describedby", screen.getByRole("tooltip").id);
    await u.unhover(b);
    expect(screen.queryByRole("tooltip")).toBeNull();
    await u.tab();
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    await u.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).toBeNull();
    await u.tab();
    await u.click(b);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    await u.click(b);
    await u.unhover(b);
    await u.tab();
    expect(screen.queryByRole("tooltip")).toBeNull();
  });
});
