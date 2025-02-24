//package org.daylongevent.daylongcafe.service;
//
//import jakarta.annotation.PostConstruct;
//import java.io.IOException;
//import java.security.GeneralSecurityException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import lombok.AllArgsConstructor;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//@Service
//@AllArgsConstructor
//public class CashedUsersService {
//
//
//    public List<List<Object>> cachedUserList = new ArrayList<>();
//    private long lastUpdated = 0;
//    private static final long CACHE_DURATION = 5 * 60 * 1000;
//
//    public void refreshCache() throws IOException, GeneralSecurityException {
//        long now = System.currentTimeMillis();
//
//        if (now - lastUpdated < CACHE_DURATION) {
//            return;
//        }
//
//        List<List<Object>> sheetData = googleSheetsService.getSheetData();
//
//        if (sheetData == null || sheetData.isEmpty()) {
//            return;
//        }
//
//        // (번호, 소비 잔 수) 변환
//        List<List<Object>> tempList = sheetData.stream()
//            .map(row -> {
//                if (row.size() < 2) return null;
//                try {
//                    List<Object> userList = new ArrayList<>();
//                    userList.add(row.get(0).toString());
//                    userList.add(Integer.parseInt(row.get(1).toString()));  // 소비 잔 수
//                    return userList;
//                } catch (NumberFormatException e) {
//                    return null;
//                }
//            })
//            .filter(Objects::nonNull)
//            .sorted((a, b) -> Integer.compare(safeCastToInt(b.get(1)), safeCastToInt(a.get(1)))) // 내림차순 정렬
//            .toList();
//
//
//        // 랭킹 계산
//        List<List<Object>> rankedList = new ArrayList<>();
//        int rank = 0, prevCups = -1, count = 0;
//
//        for (List<Object> user : tempList) {
//            int cups = (int) user.get(1);
//
//            if (cups != prevCups) {
//                rank += count;
//                count = 1;
//            } else {
//                count++;
//            }
//
//            rankedList.add(List.of(user.get(0), cups, rank + 1)); // (번호, 소비 잔 수, 랭킹) 저장
//            prevCups = cups;
//        }
//
//        cachedUserList = rankedList;
//        lastUpdated = now;
//
//
//
//    }
//
//    private int safeCastToInt(Object obj) {
//        if (obj instanceof Integer) {
//            return (int) obj;
//        } else if (obj instanceof String) {
//            try {
//                return Integer.parseInt((String) obj);
//            } catch (NumberFormatException e) {
//                return 0;
//            }
//        } else {
//            return 0;
//        }
//    }
//}
