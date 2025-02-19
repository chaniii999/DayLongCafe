package org.daylongevent.daylongcafe.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class SheetController {

    GoogleSheetsService sheetService;

    @GetMapping("/")
    public ResponseEntity<?> home() throws GeneralSecurityException, IOException {
        List<List<Object>> sheetData = sheetService.getSheetData();
        if (sheetData.isEmpty()) {
            return ResponseEntity.notFound().build();
        }


        return ResponseEntity.ok(sheetData);
    }

    @GetMapping("/search/{phoneNumber}")
    public ResponseEntity<?> search(@PathVariable String phoneNumber) throws GeneralSecurityException, IOException {
        User user = sheetService.searchByPhoneNumber(phoneNumber);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("사용자를 찾을 수 없습니다.");
        }

        String displayMessage = "010-****-" + user.getBackNumber() + " 님의 소비 잔 수는 " + user.getCupsPurchased() + "입니다.";
        return ResponseEntity.ok(displayMessage);
    }

}
