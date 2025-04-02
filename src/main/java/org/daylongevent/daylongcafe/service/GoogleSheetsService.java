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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsService {

    @Value("${google.sheets.spreadsheetId}")
    private String spreadsheetId;

    @Value("${google.sheets.clientEmail}")
    private String clientEmail;

    @Value("${google.sheets.privateKey}")
    private String privateKey;


    private static final String TOTAL_SALE_LIST_RANGE = "A1:B1000";
    private static final String MEMBER_RANGE = "MEMBER!B1:D1000";


    public List<User> cachedUserList = new ArrayList<>();
    public List<List<Object>> memberList = new ArrayList<>();

    private volatile Sheets sheetsService;

    private Sheets getSheetsService() throws GeneralSecurityException, IOException {
        if (sheetsService == null) { // 지연 초기화 (Lazy Initialization)
            synchronized (this) { // 동시성 문제 방지
                if (sheetsService == null) {
                    String formattedPrivateKey = privateKey.replace("\\n", "\n");

                    GoogleCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                        clientEmail, clientEmail, formattedPrivateKey, null, Collections.singleton(SheetsScopes.SPREADSHEETS)
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


    public List<List<Object>> getSaleListSheetData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        ValueRange response = service.spreadsheets().values()
            .get(spreadsheetId, TOTAL_SALE_LIST_RANGE)
            .execute();

        return response.getValues();
    }

    public List<List<Object>> getMemberListSheetData() throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, MEMBER_RANGE)
                .execute();

        return response.getValues();
    }



    public void refreshCache() throws IOException, GeneralSecurityException {

        List<List<Object>> saleData = getSaleListSheetData();
        List<List<Object>> memberData = getMemberListSheetData();

        if (saleData == null || saleData.isEmpty()) {
            return;
        }

        Map<String, Integer> phoneCupMap = getPhoneCupsMapFromSheetData(saleData);
        this.memberList = memberData;
        this.cachedUserList = generateUserList(phoneCupMap);
        this.cachedUserList = assignRankings(cachedUserList);
    }



    private Map<String, Integer> getPhoneCupsMapFromSheetData(List<List<Object>> sheetData) {
        Map<String, Integer> phoneCupMap = new HashMap<>();

        sheetData.forEach(row -> {
            if (row.size() < 2) return;  // 데이터가 충분하지 않으면 무시
            if (row.get(0).equals("") || row.get(1).equals("")) return;

            try {
                String phoneNumber = row.get(0).toString();
                int cups = Integer.parseInt(row.get(1).toString());

                // 기존 값이 있으면 더하고, 없으면 새로 추가
                phoneCupMap.merge(phoneNumber, cups, Integer::sum);

            } catch (NumberFormatException e) {
                // 숫자 변환 실패 시 무시
            }
        });

        return phoneCupMap;
    }

    private List<User> generateUserList(Map<String, Integer> phoneCupMap) {
        return phoneCupMap.entrySet().stream()
                .map(entry -> User.builder()
                        .backNumber(entry.getKey())  // 전화번호
                        .cups(entry.getValue())  // 합산된 컵 수
                        .build())
                .sorted((a, b) -> Integer.compare(b.getCups(), a.getCups()))  // 컵 수 내림차순 정렬
                .toList();
    }

    private List<User> assignRankings(List<User> userList) {
        List<User> rankedList = new ArrayList<>();
        int rank = 0, prevCups = -1;

        for (User user : userList) {
            int cups = user.getCups();

            if (cups != prevCups) { // 컵 수가 달라지면 새로운 등수 적용
                rank++;
            }

            // 랭킹을 User 객체에 설정
            rankedList.add(User.builder()
                    .backNumber(user.getBackNumber())  // 기존 번호
                    .cups(user.getCups())  // 기존 잔 수
                    .rank(rank)  // 계산된 랭크
                    .build());

            prevCups = cups;  // 이전 컵 수 저장
        }

        return rankedList;
    }



    public User searchByPhoneNumber(String phoneNumber) throws IOException, GeneralSecurityException {
        refreshCache();
        //String memberNumber = "010-" + phoneNumber.substring(0, 4) + "-" + phoneNumber.substring(4);
        String memberNumber = "10" + phoneNumber;
        String memberSex = "찾지못함.";
        String memberBirthDate = "찾지못함.";
        for (List<Object> row : memberList) {

            try {
                String sheetPhoneNumber = row.get(0).toString();
                if (Objects.equals(sheetPhoneNumber, memberNumber)) {
                    memberSex = row.get(1).toString();
                    memberBirthDate = row.get(2).toString();
                    break; // 필요한 정보 찾으면 루프 종료
                }
            } catch (Exception e) {
                // 데이터 형 변환 실패 시 무시
                continue;
            }
        }

        String backNumber = phoneNumber.substring(4);
        // 조회한 사용자 랭킹 찾기
        for (User user : cachedUserList) {
            if (user.getBackNumber().equals(phoneNumber)) {
                User foundUser = User.builder()
                    .backNumber(backNumber)  // 새로운 번호 설정
                    .cups(user.getCups())  // 기존 잔 수 유지
                    .rank(user.getRank())  // 기존 순위 유지
                    .requiredCupsNextRank(searchNextCups(user.getRank()) - user.getCups())  // 다음 순위로 가기 위한 잔 수 설정
                    .nextRank(searchNextRank(user.getRank()))
                    .sex(memberSex)
                    .birthDate(memberBirthDate)
                    .build();  // User 객체 생성
                appendLogEntry(foundUser, memberNumber);
                return foundUser;
            }
        }

        return null;
    }

    private void appendLogEntry(User user, String memberNumber) throws IOException, GeneralSecurityException {
        Sheets service = getSheetsService();
        String logSheetRange = "log!A:D";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String currentTime = sdf.format(new Date());

        // 기록할 데이터 리스트
        List<Object> logEntry = Arrays.asList(
                currentTime,          // 현재 시간
                memberNumber, // 사용자 폰번호 (뒤 4자리)
                user.getSex(),  // 성별
                user.getBirthDate() // 생년월일
        );

        ValueRange appendBody = new ValueRange()
                .setValues(Collections.singletonList(logEntry));

        // Google Sheets에 데이터 추가 (append)
        service.spreadsheets().values()
                .append(spreadsheetId, logSheetRange, appendBody)
                .setValueInputOption("RAW")
                .execute();
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

}