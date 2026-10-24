import type { TFunction } from "i18next";
import type { AnnouncementTemplates, AnnouncementType } from "./types";

export interface TemplateVars {
  turma?: string;
  data?: string;
  horario?: string;
  periodo?: string;
  motivo?: string;
}

// RN-COM-04, RN-COM-12: título e texto iniciais do tipo vêm do servidor (o da organização ou o da plataforma), com {campo}. O que a tela ainda não sabe vira um
// marcador entre colchetes para a pessoa preencher. A troca é em uma passada, então o valor de um campo nunca é lido como campo.
export function templateFor(t: TFunction, templates: AnnouncementTemplates, type: AnnouncementType, v: TemplateVars) {
  const p = (k: string) => t(`announcements.new.placeholders.${k}`);
  const vars: Record<string, string> = {
    turma: v.turma || p("target"),
    data: v.data || p("date"),
    horario: v.horario || p("time"),
    periodo: v.periodo || p("period"),
    motivo: v.motivo || p("reason"),
    unidade: p("unit"),
  };
  const fill = (text: string) => text.replace(/\{([a-z_]+)\}/g, (all, k: string) => vars[k] ?? all);
  const template = templates[type];
  return { title: fill(template.title), body: fill(template.body) };
}
