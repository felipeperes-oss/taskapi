package br.edu.ifsp.web.service;

import br.edu.ifsp.web.model.Usuario;

public interface AutenticacaoService {
    Usuario autenticar(String email, String senha);
}
