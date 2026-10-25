package persistencia;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hash de senhas com PBKDF2-HMAC-SHA256 e sal aleatório.
 * Formato gravado: {@code pbkdf2$<iteracoes>$<sal base64>$<hash base64>}.
 * Valores sem esse prefixo são senhas antigas em texto puro (migradas no início do servidor).
 */
public final class Senhas {

    private static final String PREFIXO = "pbkdf2";
    private static final int ITERACOES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Senhas() {}

    public static String gerarHash(String senha) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(senha, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIXO + "$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public static boolean ehHash(String armazenado) {
        return armazenado != null && armazenado.startsWith(PREFIXO + "$");
    }

    /** Compara em tempo constante; aceita também o formato antigo em texto puro. */
    public static boolean conferir(String senha, String armazenado) {
        if (senha == null || armazenado == null) return false;
        if (!ehHash(armazenado)) {
            return MessageDigest.isEqual(senha.getBytes(StandardCharsets.UTF_8), armazenado.getBytes(StandardCharsets.UTF_8));
        }
        String[] partes = armazenado.split("\\$");
        if (partes.length != 4) return false;
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(derivar(senha, sal, iteracoes), esperado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_HASH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível na JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
