import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { ModalityConfirmations } from "./ModalityConfirmations";
import { server } from "@/mocks/server";
import { axeViolations } from "@/test/axe";

const item = { modalityRowId: "m1", studentId: "s1", studentName: "Aluno Fictício", modality: "Judô", unit: "Centro", planId: "p1", plan: "Judô Mensal", planValueCents: 15000 };

describe("ModalityConfirmations (RN-MAT-13)", () => {
  it("mostra plano e valor e some ao confirmar, sem mexer em mais nada", async () => {
    const posts: string[] = [];
    server.use(
      http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([item])),
      http.post("/api/v1/portal/modality-confirmations/:id", ({ params }) => {
        posts.push(params.id as string);
        return HttpResponse.json({ modalityRowId: "m1", studentId: "s1", planId: "p1", planValueCents: 15000 }, { status: 201 });
      }),
    );
    const { container } = render(<ModalityConfirmations />);
    expect(await screen.findByText(/Plano Judô Mensal, R\$\s150,00 por mês/)).toBeInTheDocument();
    expect(await axeViolations(container)).toEqual([]);
    await userEvent.click(screen.getByRole("button", { name: "Confirmar Judô" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Confirmar Judô" })).not.toBeInTheDocument());
    expect(posts).toEqual(["m1"]);
  });

  it("falha ao confirmar avisa e mantém a pendência", async () => {
    server.use(
      http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([item])),
      http.post("/api/v1/portal/modality-confirmations/:id", () => new HttpResponse(null, { status: 500 })),
    );
    render(<ModalityConfirmations />);
    await userEvent.click(await screen.findByRole("button", { name: "Confirmar Judô" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Nada foi alterado");
    expect(screen.getByRole("button", { name: "Confirmar Judô" })).toBeInTheDocument();
  });

  it("sem pendência não mostra nada", async () => {
    server.use(http.get("/api/v1/portal/modality-confirmations", () => HttpResponse.json([])));
    const { container } = render(<ModalityConfirmations />);
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });
});
