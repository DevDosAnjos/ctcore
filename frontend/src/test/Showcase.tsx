import { useState } from "react";
import { AppLayout } from "@/components/layout/AppLayout";
import { Card, CardGrid } from "@/components/Card";
import { CharCounter } from "@/components/CharCounter";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { Modal } from "@/components/Modal";
import { SidePanel } from "@/components/SidePanel";
import { Stepper } from "@/components/Stepper";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { QueryPage } from "@/components/query/QueryPage";
import { CpfField, DateField, MoneyField, PhoneField } from "@/components/fields/fields";

const groups = [{ id: "g", label: "Grupo", items: [{ id: "a", label: "Item", href: "#a" }] }];
const rows = [{ id: "1", nome: "Ana" }];
const fetchPage = async () => ({ rows, total: 1 });
const columns = [{ key: "nome", label: "Nome", sortable: true, render: (r: { nome: string }) => r.nome }];

// Página de demonstração só para teste (axe e 360 px); fica pronta para a #10. Textos de exemplo, não publicados.
export function Showcase({ modal = false, panel = false, confirm = false }: { modal?: boolean; panel?: boolean; confirm?: boolean }) {
  const [text, setText] = useState("");
  const noop = () => {};
  return (
    <AppLayout groups={groups} activeId="a">
      <CardGrid>
        <Card title="Estados" actions={<Button variant="outline">Ação</Button>}>
          <LoadingState />
          <EmptyState title="Vazio" text="Nada aqui" action={<Button>Criar</Button>} />
          <ErrorState onRetry={noop} />
          <ForbiddenState />
        </Card>
        <Card title="Campos">
          <CharCounter aria-label="Observação" value={text} max={100} onChange={setText} />
          <CpfField label="CPF" onChange={noop} />
          <PhoneField label="Telefone" onChange={noop} />
          <MoneyField label="Valor" onChange={noop} />
          <DateField label="Data" onChange={noop} />
        </Card>
        <Card title="Etapas">
          <Stepper
            onFinish={noop}
            steps={[
              { id: "1", title: "Um", valid: true, content: <p>Primeira</p> },
              { id: "2", title: "Dois", valid: true, content: <p>Segunda</p> },
            ]}
          />
        </Card>
      </CardGrid>
      <QueryPage columns={columns} fetchPage={fetchPage} rowKey={(r) => r.id} />
      <Modal open={modal} onOpenChange={noop} title="Janela">
        <p>Conteúdo</p>
      </Modal>
      <SidePanel open={panel} onOpenChange={noop} title="Painel">
        <p>Conteúdo</p>
      </SidePanel>
      <ConfirmDialog open={confirm} onOpenChange={noop} title="Confirmar" summary="Resumo" reason="required" typedText="DESATIVAR" onConfirm={noop} />
    </AppLayout>
  );
}
