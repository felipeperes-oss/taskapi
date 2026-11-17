package br.edu.ifsp.web.service;

import br.edu.ifsp.web.dao.ConnectionFactory;
import br.edu.ifsp.web.dao.UsuarioDAO;
import br.edu.ifsp.web.model.Usuario;

public class AutenticacaoServiceImpl implements AutenticacaoService {
    private static final String HASH_AUSENTE = "pbkdf2-sha256$600000$yR8Yd1zxZBr7eT5F1ls/gw==$9q/6YlpM3CiPY6WcoOWESVAgnCbe2nGAVb4OdSw9PhQ=";
    private final Transacao transacao;

    public AutenticacaoServiceImpl(ConnectionFactory connectionFactory) {
        transacao = new Transacao(connectionFactory);
    }

    @Override
    public Usuario autenticar(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            throw new AutenticacaoException("E-mail ou senha inválidos.");
        }
        return transacao.executar(conexao -> {
            UsuarioDAO.Credencial credencial = new UsuarioDAO(conexao).buscarCredencial(email);
            String hash = credencial == null ? HASH_AUSENTE : credencial.senhaHash();
            boolean senhaCorreta = SenhaPBKDF2.conferir(senha, hash);
            if (credencial == null || !senhaCorreta) {
                throw new AutenticacaoException("E-mail ou senha inválidos.");
            }
            return credencial.usuario();
        });
    }
}
