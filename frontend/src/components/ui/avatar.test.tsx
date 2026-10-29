import { render, screen } from "@testing-library/react";
import { Avatar, Person, avatarTone, initials } from "./avatar";

describe("Avatar (CA18)", () => {
  it("iniciais até duas letras e cor estável", () => {
    expect(initials("maria da silva")).toBe("MS");
    expect(initials("Pedro")).toBe("P");
    const { unmount } = render(<Avatar name="Ana Lima" />);
    const a = screen.getByRole("img", { name: "Ana Lima" });
    expect(a).toHaveTextContent("AL");
    const tone = a.getAttribute("data-tone");
    unmount();
    render(<Avatar name="Ana Lima" />);
    expect(screen.getByRole("img").getAttribute("data-tone")).toBe(tone);
  });
  it("nomes diferentes usam tons da paleta de tokens", () => {
    const tones = new Set(["Ana", "Bruno", "Carla", "Diego", "Elisa", "Fabio", "Gil"].map(avatarTone));
    expect(tones.size).toBeGreaterThan(1);
  });
  it("Person mostra avatar, nome e linha secundária", () => {
    render(<Person name="Ana Lima" secondary="Jiu-jitsu" />);
    expect(screen.getByText("Ana Lima")).toBeInTheDocument();
    expect(screen.getByText("Jiu-jitsu")).toBeInTheDocument();
    expect(screen.getByText("AL")).toBeInTheDocument();
  });
});
