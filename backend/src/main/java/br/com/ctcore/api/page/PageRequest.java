package br.com.ctcore.api.page;

import br.com.ctcore.api.error.ApiValidationException;
import java.util.Set;

/** Paginação e ordenação de toda lista: page a partir de 1, size 25/50/100, sort "campo,asc|desc" (D6, D7). */
public record PageRequest(int page, int size, Sort sort) {

    public record Sort(String field, boolean desc) {}

    private static final Set<Integer> SIZES = Set.of(25, 50, 100);

    /** Valor inválido não é corrigido: lança 400. {@code allowedSort}: campos que a rota aceita. */
    public static PageRequest of(Integer page, Integer size, String sort, Set<String> allowedSort) {
        int p = page == null ? 1 : page;
        int s = size == null ? 25 : size;
        if (p < 1) throw new ApiValidationException("page", "deve ser 1 ou maior");
        if (!SIZES.contains(s)) throw new ApiValidationException("size", "deve ser 25, 50 ou 100");
        return new PageRequest(p, s, parseSort(sort, allowedSort));
    }

    private static Sort parseSort(String sort, Set<String> allowed) {
        if (sort == null || sort.isBlank()) return null;
        String[] parts = sort.split(",", -1);
        boolean ok = parts.length == 2 && allowed.contains(parts[0])
                && (parts[1].equals("asc") || parts[1].equals("desc"));
        if (!ok) throw new ApiValidationException("sort", "use campo,asc ou campo,desc com um campo permitido");
        return new Sort(parts[0], parts[1].equals("desc"));
    }

    public int offset() {
        return (page - 1) * size;
    }
}
