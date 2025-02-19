package org.daylongevent.daylongcafe.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import org.daylongevent.daylongcafe.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    @Value("${google.sheets.spreadsheetId}")
    private String spreadsheetId;

    @Value("${google.sheets.clientEmail}")
    private String clientEmail;

    @Value("${google.sheets.privateKey}")
    private String privateKey;

    private static final String RANGE = "A1:B100"; // 전화번호, 구매한 잔 수

    /**
     * Google Sheets API 클라이언트 생성
     */
    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        String formattedPrivateKey = privateKey.replace("\\n", "\n");

        GoogleCredentials credentials = ServiceAccountCredentials.fromPkcs8(
            clientEmail, clientEmail, formattedPrivateKey, null, Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY)
        );
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Sheets.Builder(
            com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
            com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(),
            requestInitializer
        ).setApplicationName("Google Sheets API Java").build();
    }


    /**
     * 구글 시트에서 데이터 조회
     */
    public List<List<Object>> getSheetData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        ValueRange response = service.spreadsheets().values()
            .get(spreadsheetId, RANGE)
            .execute();

        return response.getValues();
    }

    public User searchByPhoneNumber(String phoneNumber) throws IOException, GeneralSecurityException {
        List<List<Object>> values = getSheetData();

        if (values == null || values.isEmpty()) {
            System.out.println("시트가 비어있음.");
            return null;
        }

        for (List<Object> row : values) {
            String cellPhoneNumber = (String) row.get(0);
            String cupsPurchased = (String) row.get(1);
            if (cellPhoneNumber.equals(phoneNumber)) {
                String backNumber = cellPhoneNumber.substring(4);
                return User.builder()
                   .backNumber(backNumber)
                    .cupsPurchased(Integer.parseInt(cupsPurchased))
                    .rank(1)
                   .build();
            }
        }


        return null; // 전화번호가 없으면 null 반환
    }

}

