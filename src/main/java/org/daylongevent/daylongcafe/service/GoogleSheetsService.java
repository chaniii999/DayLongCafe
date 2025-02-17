package org.daylongevent.daylongcafe.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleSheetsService {

    @Value("${google.sheets.spreadsheetId}")
    private String spreadsheetId;

    @Value("${google.sheets.clientEmail}")
    private String clientEmail;

    @Value("${google.sheets.privateKey}")
    private String privateKey;


}