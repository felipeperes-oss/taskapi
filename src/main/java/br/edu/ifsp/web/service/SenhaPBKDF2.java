package br.edu.ifsp.web.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class SenhaPBKDF2 {
    private SenhaPBKDF2() {
    }

    static boolean conferir(String senha, String hashArmazenado) {
        try {
            String[] partes = hashArmazenado.split("\\$", -1);
            if (partes.length != 4 || !"pbkdf2-sha256".equals(partes[0])) {
                throw new IllegalArgumentException("Formato de hash inválido.");
            }
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            if (iteracoes <= 0 || salt.length != 16 || esperado.length != 32) {
                throw new IllegalArgumentException("Parâmetros do hash inválidos.");
            }
            PBEKeySpec especificacao = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, esperado.length * 8);
            try {
                byte[] calculado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(especificacao).getEncoded();
                return MessageDigest.isEqual(esperado, calculado);
            } finally {
                especificacao.clearPassword();
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FalhaInternaException("Não foi possível verificar a credencial armazenada.", e);
        }
    }
}
