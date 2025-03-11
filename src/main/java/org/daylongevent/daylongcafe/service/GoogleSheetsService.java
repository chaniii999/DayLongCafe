package org.daylongevent.daylongcafe.service;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.daylongevent.daylongcafe.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

//    @Value("${GOOGLE_SHEETS_SPREADSHEET_ID}")
//    private String spreadsheetId;
//
//    @Value("${GOOGLE_SHEETS_CLIENT_EMAIL}")
//    private String clientEmail;
//
//    @Value("${GOOGLE_SHEETS_PRIVATE_KEY}")
//    private String privateKey;
    @Value("${google.sheets.spreadsheetId}")
    private String spreadsheetId;

    @Value("${google.sheets.clientEmail}")
    private String clientEmail;

    @Value("${google.sheets.privateKey}")
    private String privateKey;


    private static final String RANGE = "A1:B300";


    public List<User> cachedUserList = new ArrayList<>();
    private long lastUpdated = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000;


    private volatile Sheets sheetsService;

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        if (sheetsService == null) { // 지연 초기화 (Lazy Initialization)
            synchronized (this) { // 동시성 문제 방지
                if (sheetsService == null) {
                    String formattedPrivateKey = privateKey.replace("\\n", "\n");

                    GoogleCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                        clientEmail, clientEmail, formattedPrivateKey, null, Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY)
                    );
                    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

                    sheetsService = new Sheets.Builder(
                        com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                        com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance(),
                        requestInitializer
                    ).setApplicationName("Google Sheets API Java").build();
                }
            }
        }
        return sheetsService;
    }


    public List<List<Object>> getSheetData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        ValueRange response = service.spreadsheets().values()
            .get(spreadsheetId, RANGE)
            .execute();

        return response.getValues();
    }

    public void refreshCache() throws IOException, GeneralSecurityException {
        long now = System.currentTimeMillis();

//        if (now - lastUpdated < CACHE_DURATION) {
//            return;
//        }

        List<List<Object>> sheetData = getSheetData();

        if (sheetData == null || sheetData.isEmpty()) {
            return;
        }

        Map<String, Integer> phoneCupMap = new HashMap<>();

        sheetData.forEach(row -> {
            if (row.size() < 2) return;  // row에 값이 2개 미만이면 skip
            if (row.get(0).equals("") || row.get(1).equals("") ) return;
            try {
                String phoneNumber = row.get(0).toString();
                int cups = Integer.parseInt(row.get(1).toString());

                // 전화번호가 이미 맵에 있으면 기존 값에 더하고, 없으면 새로 추가
                phoneCupMap.merge(phoneNumber, cups, Integer::sum);

            } catch (NumberFormatException e) {
                // 숫자 형변환 실패 시 무시 (다시 넘어가도록 처리)
            }
        });

        List<User> tempList = phoneCupMap.entrySet().stream()
            .map(entry -> User.builder()
                .backNumber(entry.getKey())  // 전화번호
                .cups(entry.getValue())  // 합산된 컵 수
                .build())
            .sorted((a, b) -> Integer.compare(b.getCups(), a.getCups()))  // 컵 수 내림차순 정렬
            .toList();  // 리스트로 수집

        cachedUserList = tempList;  // 캐시 갱신

        // 랭킹 계산
        List<User> rankedList = new ArrayList<>();
        int rank = 0, prevCups = -1, sameRankCount = 1;

        for (User user : tempList) {
            int cups = user.getCups();

            if (cups != prevCups) { // 컵 수가 달라지면 새로운 등수 적용
                //rank += getSameCupsCount(cups);
                rank += 1;
            }
            else {
                sameRankCount++;
            }

            // 랭킹을 User 객체에 설정
            User rankedUser = User.builder()
                .backNumber(user.getBackNumber())  // 기존 번호
                .cups(user.getCups())  // 기존 잔 수
                .rank(rank)  // 계산된 랭크
                .build();  // 랭킹 반영된 User 객체 생성

            rankedList.add(rankedUser); // 랭킹이 포함된 사용자 저장
            prevCups = cups;  // 이전 컵 수 저장
        }

        cachedUserList = rankedList;  // 캐시 갱신
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
    public User searchByPhoneNumber(String phoneNumber) throws IOException, GeneralSecurityException {
        refreshCache();

        String backNumber = phoneNumber.substring(4);
        // 조회한 사용자 랭킹 찾기
        for (User user : cachedUserList) {
            if (user.getBackNumber().equals(phoneNumber)) {
                return User.builder()
                    .backNumber(backNumber)  // 새로운 번호 설정
                    .cups(user.getCups())  // 기존 잔 수 유지
                    .rank(user.getRank())  // 기존 순위 유지
                    .requiredCupsNextRank(searchNextCups(user.getRank()) - user.getCups())  // 다음 순위로 가기 위한 잔 수 설정
                    .nextRank(searchNextRank(user.getRank()))
                    .build();  // User 객체 생성
            }
        }


        return null;
    }

    public int searchNextCups(int userRank) {

        int findRank = 0;
        if (userRank > 10) findRank = 10;
        if (userRank <= 10) findRank = 5;
        if (userRank == 5) findRank = 3;
        if (userRank <= 4) findRank = userRank -1;
        if (userRank == 1) return 0;

        while (findRank > 0) {
            for (User user : cachedUserList) {
                if (user.getRank() == findRank) {
                    return user.getCups(); // 해당 순위 컵스 반환
                }
            }
            findRank--; // 만약 목표 순위가 없으면 상위 랭크 탐색
        }
        return 0;
    }

    public int searchNextRank(int userRank) {

        int findRank = 0;
        if (userRank > 10) findRank = 10;
        if (userRank <= 10) findRank = 5;
        if (userRank == 5) findRank = 3;
        if (userRank <= 4) findRank = userRank -1;
        if (userRank == 1) return 0;
        while (findRank > 0) {
            for (User user : cachedUserList) {
                if (user.getRank() == findRank) {
                    return findRank;
                }
            }
            findRank--; // 만약 목표 순위가 없으면 상위 랭크 탐색
        }
        return 0;
    }

    public List<User> getAllUsersByRank() throws IOException, GeneralSecurityException {
        refreshCache();

        return cachedUserList.stream()
            .map(user -> User.builder()
                .backNumber(user.getBackNumber()) // 뒷번호만 저장
                .cups(user.getCups()) // 소비 잔 수
                .rank(user.getRank()) // 랭킹
                .requiredCupsNextRank(Math.max(0, searchNextCups(user.getRank())-user.getCups()))
                .build())
            .collect(Collectors.toList());
    }

    public int getSameCupsCount(int cups) throws IOException, GeneralSecurityException {

        return (int) cachedUserList.stream()
            .filter(user -> user.getCups() == cups)
            .count();
    }



}