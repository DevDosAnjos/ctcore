package br.com.ctcore.storage;

/**
 * Armazenamento de arquivos por organização (RN-ORG-03). A organização vem sempre do OrganizationContext;
 * o nome é relativo à pasta da organização. Sem organização no contexto, todo método lança AccessDeniedException.
 */
public interface FileStorage {

    int MAX_BYTES = 5 * 1024 * 1024;
    int MAX_NAME = 200;

    /** O arquivo não existe na pasta da organização do contexto. */
    class NotFound extends RuntimeException {
        public NotFound(String name) { super("File not found: " + name); }
    }

    void put(String name, byte[] content, String contentType);

    /** Conteúdo do arquivo; NotFound se não existir na organização. */
    byte[] get(String name);

    /** URL de leitura assinada e temporária; NotFound se o arquivo não existir na organização. */
    String readUrl(String name);

    /** NotFound se o arquivo não existir na organização. */
    void delete(String name);

    /** Recusa nomes que saem da pasta da organização. */
    static void checkName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME || name.contains("/") || name.contains("\\")
                || name.contains("..") || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid file name");
        }
    }

    /** Recusa conteúdo vazio ou acima de 5 MB. */
    static void checkSize(byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("File must have between 1 byte and 5 MB");
        }
    }
}
