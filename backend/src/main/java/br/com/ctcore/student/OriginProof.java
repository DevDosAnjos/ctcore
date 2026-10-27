package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.storage.FileKind;
import br.com.ctcore.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * RN-GRA-09, RN-MAT-09 (R7): comprovante da graduação de origem, enviado antes da conclusão da matrícula. Guarda JPG, PNG, WebP ou PDF de até 5 MB
 * na pasta da organização, com nome gerado no servidor (que inclui o aluno), e devolve a chave que a conclusão referencia.
 */
@Service
public class OriginProof {
    static final String PREFIX = "origin-proof-";

    public record Stored(String proofKey) {}

    private final Students students;
    private final FileStorage storage;
    private final OrganizationTransactions transactions;
    private final AuditService audit;

    public OriginProof(Students students, FileStorage storage, @Lazy OrganizationTransactions transactions, AuditService audit) {
        this.students = students;
        this.storage = storage;
        this.transactions = transactions;
        this.audit = audit;
    }

    private static ApiStatusException rule(String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field("file", message)));
    }

    public Stored store(AccountPrincipal who, UUID studentId, byte[] content, String ip) {
        students.requireAccess(who);
        if (content == null || content.length == 0) throw rule("obrigatório");
        if (content.length > FileStorage.MAX_BYTES) throw rule("arquivo acima de 5 MB");
        FileKind kind = FileKind.of(content);
        if (kind == null) throw rule("use JPG, PNG, WebP ou PDF");
        // Sem alcance ou fora da organização: 404 antes de gravar o arquivo.
        UUID unit = transactions.execute(() -> students.detail(who, studentId).registeredUnitId());
        String key = PREFIX + studentId + "-" + UUID.randomUUID() + "." + kind.extension();
        storage.put(key, content, kind.contentType());
        transactions.execute(() -> {
            // A chave não vai para a auditoria (RN-LGP): só o fato do envio.
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "student.origin_proof_uploaded", "student",
                    studentId.toString(), unit, null, Map.of("type", kind.contentType()), null, ip));
            return null;
        });
        return new Stored(key);
    }

    /** A chave é deste aluno e o arquivo existe na pasta da organização de quem chama. */
    boolean valid(UUID studentId, String key) {
        if (key == null || !key.startsWith(PREFIX + studentId + "-")) return false;
        try {
            storage.readUrl(key);
            return true;
        } catch (FileStorage.NotFound | IllegalArgumentException e) {
            return false;
        }
    }
}
