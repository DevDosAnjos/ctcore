package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.storage.FileStorage;
import java.nio.charset.StandardCharsets;
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

    /** Tipo pelo conteúdo, não pelo nome: devolve {extensão, tipo} ou nulo. */
    static String[] kind(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return new String[] {"jpg", "image/jpeg"};
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return new String[] {"png", "image/png"};
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF") && new String(b, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
            return new String[] {"webp", "image/webp"};
        }
        if (b.length >= 5 && new String(b, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) return new String[] {"pdf", "application/pdf"};
        return null;
    }

    public Stored store(AccountPrincipal who, UUID studentId, byte[] content, String ip) {
        students.requireAccess(who);
        if (content == null || content.length == 0) throw rule("obrigatório");
        if (content.length > FileStorage.MAX_BYTES) throw rule("arquivo acima de 5 MB");
        String[] kind = kind(content);
        if (kind == null) throw rule("use JPG, PNG, WebP ou PDF");
        // Sem alcance ou fora da organização: 404 antes de gravar o arquivo.
        UUID unit = transactions.execute(() -> students.detail(who, studentId).registeredUnitId());
        String key = PREFIX + studentId + "-" + UUID.randomUUID() + "." + kind[0];
        storage.put(key, content, kind[1]);
        transactions.execute(() -> {
            // A chave não vai para a auditoria (RN-LGP): só o fato do envio.
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "student.origin_proof_uploaded", "student",
                    studentId.toString(), unit, null, Map.of("type", kind[1]), null, ip));
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
