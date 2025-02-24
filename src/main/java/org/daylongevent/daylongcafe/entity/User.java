package org.daylongevent.daylongcafe.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@Builder
@Setter
public class User {
    private String backNumber;
    private int cups;
    private int rank;
    private int requiredCupsNextRank; // 다음 리워드 순위에 들기위한 필요 잔 수
}

// # 20위 이상인 경우
// " 010-****-1234 님의 랭킹은 25위입니다. 소비잔 수는 3잔 입니다. 20위까지 n잔(20위의 잔 수 - 내 소비잔 수 + 1) 남았습니다.

// # 20위 이하 6위 이상 인 경우
// " 010-****-1234 님의 랭킹은 18위입니다. 소비잔 수는 10잔 입니다. 5위까지 n잔(5위의 잔 수 - 내 소비잔 수 + 1) 남았습니다.

// # 1위 인 경우
// " 010-****-1234 님은 현재 소비왕입니다!
