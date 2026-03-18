package com.example.server.service;

import com.example.server.dto.CreateMissionDTO;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DaeguFestivalCrawler {

    private static final String BASE_URL = "https://tour.daegu.go.kr/index.do?menu_id=00002932";
    private final MissionService missionService;

    /**
     * 대구시 공식 관광 사이트에서 연간 축제·행사 일정을 크롤링하여
     * 미션 콘텐츠로 자동 생성
     */
    public List<CreateMissionDTO> crawlAndSaveFestivals() throws IOException {
        List<CreateMissionDTO> crawledMissions = new ArrayList<>();

        Document doc = Jsoup.connect(BASE_URL)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

        // 축제 항목 파싱
        Elements festivals = doc.select("li.board_list_li");

        for (Element festival : festivals) {
            try {
                // 축제명
                String title = festival.select("strong.tit").text();
                if (title.isEmpty()) continue;

                // 기간
                String period = festival.select("span.period").text();

                // 장소
                String location = festival.select("span.place").text();

                // 미션 내용 생성
                String content = buildMissionContent(title, period, location);

                // 이미지 URL (없으면 기본값)
                String imageUrl = festival.select("img").attr("src");
                if (imageUrl.isEmpty()) imageUrl = null;

                CreateMissionDTO mission = new CreateMissionDTO(
                        null, title, content, imageUrl, "축제/행사"
                );
                crawledMissions.add(mission);

                // DB에 미션으로 저장
                missionService.createMission(mission, "축제/행사");

            } catch (Exception e) {
                // 파싱 실패한 항목은 스킵
                System.out.println("파싱 실패: " + e.getMessage());
            }
        }
        return crawledMissions;
    }

    private String buildMissionContent(String title, String period, String location) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(title).append("]에 방문하세요!\n");
        if (!period.isEmpty()) sb.append("기간: ").append(period).append("\n");
        if (!location.isEmpty()) sb.append("장소: ").append(location);
        return sb.toString();
    }
}
