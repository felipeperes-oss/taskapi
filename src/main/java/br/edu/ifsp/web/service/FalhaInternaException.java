package br.edu.ifsp.web.service;

public class FalhaInternaException extends AplicacaoException {
    public FalhaInternaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
