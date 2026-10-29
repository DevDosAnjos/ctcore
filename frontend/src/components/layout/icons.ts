import {
  Award, Baby, Building, Building2, CalendarDays, CalendarOff, ChartColumn, Circle, CreditCard, Download, Dumbbell, FileText, Gift, House, Inbox,
  Layers, LayoutGrid, LifeBuoy, MapPin, Megaphone, ScrollText, Settings, Ticket, Type, Upload, UserCheck, UserCog, Users, Wallet,
  type LucideIcon,
} from "lucide-react";

// Só os ícones do catálogo entram no pacote (nada de `icons` inteiro); nome desconhecido cai no círculo.
const ICONS: Record<string, LucideIcon> = {
  Award, Baby, Building, Building2, CalendarDays, CalendarOff, ChartColumn, CreditCard, Download, Dumbbell, FileText, Gift, House, Inbox,
  Layers, LayoutGrid, LifeBuoy, MapPin, Megaphone, ScrollText, Settings, Ticket, Type, Upload, UserCheck, UserCog, Users, Wallet,
};
export const iconFor = (name?: string): LucideIcon => (name && ICONS[name]) || Circle;
