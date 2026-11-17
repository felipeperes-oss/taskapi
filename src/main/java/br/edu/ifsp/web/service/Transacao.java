package br.edu.ifsp.web.service;

import br.edu.ifsp.web.dao.ConnectionFactory;
import java.sql.Connection;
import java.sql.SQLException;

final class Transacao {
    private final ConnectionFactory connectionFactory;

    Transacao(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    <T> T executar(Operacao<T> operacao) {
        try (Connection conexao = connectionFactory.getConnection()) {
            conexao.setAutoCommit(false);
            try {
                T resultado = operacao.executar(conexao);
                conexao.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                try {
                    conexao.rollback();
                } catch (SQLException falhaRollback) {
                    e.addSuppressed(falhaRollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new FalhaInternaException("Não foi possível executar a operação no banco de dados.", e);
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new RegraDominioException(e.getMessage(), e);
        }
    }

    @FunctionalInterface
    interface Operacao<T> {
        T executar(Connection conexao) throws SQLException;
    }
}
