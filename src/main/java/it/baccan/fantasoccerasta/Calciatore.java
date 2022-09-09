/*
 * Copyright (c) 2019 Matteo Baccan
 * http://www.baccan.it
 * 
 * Distributed under the GPL v3 software license, see the accompanying
 * file LICENSE or http://www.gnu.org/licenses/gpl.html.
 * 
 */
package it.baccan.fantasoccerasta;

import lombok.Data;

/**
 *
 * @author Matteo baccan
 */
@Data
public class Calciatore {

    private String codice;
    private String ruolo;
    private String nome;
    private String squadra;
    private String fantamedia;
    private String presenze;
    private String infortunato;
    private String rigorista;
    private String evidenzia;
    private String serie;
    private String eta;

}
