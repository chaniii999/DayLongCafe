package org.daylongevent.daylongcafe.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.daylongevent.daylongcafe.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    @Value("${google.sheets.spreadsheetId}")
    private String spreadsheetId;

    @Value("${google.sheets.clientEmail}")
    private String clientEmail;

    @Value("${google.sheets.privateKey}")
    private String privateKey;

    private static final String RANGE = "A1:B100";


    public List<List<Object>> cachedUserList = new ArrayList<>();
    private long lastUpdated = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000;


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

    public List<List<Object>> getSheetData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        ValueRange response = service.spreadsheets().values()
            .get(spreadsheetId, RANGE)
            .execute();

        return response.getValues();
    }

    public User searchByPhoneNumber(String phoneNumber) throws IOException, GeneralSecurityException {
        refreshCache();

        // 조회한 사용자 랭킹 찾기
        for (List<Object> user : cachedUserList) {
            if (user.get(0).equals(phoneNumber)) {
                String backNumber = phoneNumber.substring(4);
                return User.builder()
                    .backNumber(backNumber)
                    .cups(Integer.parseInt(user.get(1).toString()))
                    .rank(Integer.parseInt(user.get(2).toString())) // 랭킹 반영
                    .requiredCupsNextRank(searchCupsByRank(Integer.parseInt(user.get(2).toString()))-Integer.parseInt(user.get(1).toString()))
                    .build();
            }
        }

        return null;
    }

    public void refreshCache() throws IOException, GeneralSecurityException {
        long now = System.currentTimeMillis();

        if (now - lastUpdated < CACHE_DURATION) {
            return;
        }

        List<List<Object>> sheetData = getSheetData();

        if (sheetData == null || sheetData.isEmpty()) {
            return;
        }

        // (번호, 소비 잔 수) 변환
        List<List<Object>> tempList = sheetData.stream()
            .map(row -> {
                if (row.size() < 2) return null;
                try {
                    List<Object> userList = new ArrayList<>();
                    userList.add(row.get(0).toString());
                    userList.add(Integer.parseInt(row.get(1).toString()));  // 소비 잔 수
                    return userList;
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .sorted((a, b) -> Integer.compare(safeCastToInt(b.get(1)), safeCastToInt(a.get(1)))) // 내림차순 정렬
            .toList();


        // 랭킹 계산
        List<List<Object>> rankedList = new ArrayList<>();
        int rank = 1, prevCups = -1, sameRankCount = 0;

        for (List<Object> user : tempList) {
            int cups = (int) user.get(1);

            if (cups != prevCups) { // 컵 수가 달라지면 새로운 등수 적용
                rank += sameRankCount;
                sameRankCount = 1;
            } else {
                sameRankCount++;
            }

            rankedList.add(List.of(user.get(0), cups, rank)); // (번호, 소비 잔 수, 랭킹) 저장
            prevCups = cups;
        }

        cachedUserList = rankedList;
        lastUpdated = now;



    }

    private int safeCastToInt(Object obj) {
        if (obj instanceof Integer) {
            return (int) obj;
        } else if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public int searchCupsByRank(int userRank) {

        int findRank = 0;
        if (userRank > 20) findRank = 20;
        if (userRank <= 20) findRank = 5;
        if (userRank <= 5) findRank = userRank -1;
        if (userRank == 1) return 0;
        for (List<Object>user : cachedUserList) {
            // 현재 랭킹과 맞는 사용자 찾기// 사용자 랭킹
            if ((int) user.get(2) == findRank) {
                // 해당 랭크의 소비 잔 수 반환
                return (int) user.get(1);  // 소비 잔 수
            }
        }
        return 0;
    }

    public List<User> getAllUsersByRank() throws IOException, GeneralSecurityException {
        refreshCache(); // 캐시 갱신

        return cachedUserList.stream()
            .map(user -> User.builder()
                .backNumber(user.get(0).toString().substring(4)) // 뒷번호만 저장
                .cups((int) user.get(1)) // 소비 잔 수
                .rank((int) user.get(2)) // 랭킹
                .requiredCupsNextRank(searchCupsByRank((int)user.get(2))-(int) user.get(1))
                .build())
            .collect(Collectors.toList());
    }


}