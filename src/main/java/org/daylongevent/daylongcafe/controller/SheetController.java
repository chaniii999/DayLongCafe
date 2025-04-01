package org.daylongevent.daylongcafe.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.daylongevent.daylongcafe.entity.User;
import org.daylongevent.daylongcafe.service.GoogleSheetsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1/users")
public class SheetController {

    @Autowired
    private GoogleSheetsService sheetService;

//    @GetMapping("/")
//    public ResponseEntity<?> home() throws GeneralSecurityException, IOException {
//        List<User> allUsersByRank = sheetService.getAllUsersByRank();
//
//        return ResponseEntity.ok(allUsersByRank);
//    }

//    @GetMapping("/search/{phoneNumber}/message")
//    public ResponseEntity<?> searchMessage(@PathVariable String phoneNumber) throws GeneralSecurityException, IOException {
//        User user = sheetService.searchByPhoneNumber(phoneNumber);
//
//        if (user == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
//        }
//
//        String displayMessage =
//            "010-****-" + user.getBackNumber() + " 님의 소비 잔 수는 " + user.getCups() + "이고 "
//                + user.getRank() + "등입니다." +
//                "다음 랭킹까지" + user.getRequiredCupsNextRank() + "잔 남았습니다.\n" +
//                    "성별" + user.getSex() + "생일" + user.getBirthDate() + "입니다.";
//
//        if (user.getRank() == 1) {
//            displayMessage = "010-****-" + user.getBackNumber() + " 님의 소비 잔 수는 " + user.getCups() + "이고 "+ user.getRank() + "등입니다.\n" +
//            "성별" + user.getSex() + "생일" + user.getBirthDate() + "입니다.";
//        }
//        return ResponseEntity.ok(displayMessage);
//    }

    @GetMapping("/search/{phoneNumber}")
    public ResponseEntity<?> search(@PathVariable String phoneNumber) throws GeneralSecurityException, IOException {
        User user = sheetService.searchByPhoneNumber(phoneNumber);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("phoneNumber", "010-****-" + user.getBackNumber());
        response.put("cups", user.getCups());
        response.put("rank", user.getRank());
        response.put("nextRank", user.getNextRank());

        if (user.getRank() > 1) {
            response.put("cupsRequiredNextRank", user.getRequiredCupsNextRank());
        }

        return ResponseEntity.ok(response);
    }




}
