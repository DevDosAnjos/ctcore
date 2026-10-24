import type { TFunction } from "i18next";
import type { AnnouncementType } from "./types";

export interface TemplateVars {
  turma?: string;
  data?: string;
  horario?: string;
  periodo?: string;
  motivo?: string;
}

// RN-COM-04: título e texto iniciais do tipo (em pt-BR.json). O que a tela ainda não sabe vira um marcador entre colchetes para a pessoa preencher.
export function templateFor(t: TFunction, type: AnnouncementType, v: TemplateVars) {
  const p = (k: string) => t(`announcements.new.placeholders.${k}`);
  const vars = { turma: v.turma || p("target"), data: v.data || p("date"), horario: v.horario || p("time"), periodo: v.periodo || p("period"), motivo: v.motivo || p("reason") };
  return { title: t(`announcements.new.templates.${type}.title`), body: t(`announcements.new.templates.${type}.body`, vars) };
}
