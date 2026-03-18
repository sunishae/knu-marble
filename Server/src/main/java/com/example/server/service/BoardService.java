package com.example.server.service;

import com.example.server.common.exception.CustomException;
import com.example.server.common.exception.ErrorCode;
import com.example.server.dto.BoardDTO;
import com.example.server.entity.*;
import com.example.server.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final TileRepository tileRepository;
    private final MissionRepository missionRepository;
    private final MemberRepository memberRepository;
    private final MissionSummitRepository missionSummitRepository;

    /**
     * 유저 회원가입 시 보드를 생성하고, 20개의 타일과 미션을 할당한 후 반환
     */
    @Transactional
    public BoardDTO createBoard(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_MEMBER));

        Board board = new Board();
        board.setMember(member);
        board.setMemberPosition(0);

        // 모든 미션을 한 번에 조회
        List<Mission> allMissions = missionRepository.findAll();
        
        // 미션 개수가 부족할 경우 예외 처리
        if (allMissions.size() < 20) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_MISSIONS);
        }

        // 리스트를 무작위로 섞음
        Collections.shuffle(allMissions);

        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // 섞인 리스트의 앞에서부터 20개를 순차적으로 할당
            Mission mission = allMissions.get(i); 
            
            Tile tile = new Tile();
            tile.setBoard(board);
            tile.setMission(mission);
            tile.setOrder(i);
            tile.setState(TileState.OPEN);
            tiles.add(tile);
        }

        board.setTiles(tiles);
        boardRepository.save(board);
        tileRepository.saveAll(tiles);

        List<BoardDTO.TileDTO> tileDTOs = convertTilesToDTOs(tiles, member.getMemberId());
        return new BoardDTO(board.getId(), board.getMemberPosition(), tileDTOs);
    }

    /**
     * 한 바퀴 돌았을 때 완료한 미션을 새로운 미션으로 갱신
     */
    @Transactional
    public void updateMissionsOnBoard(Board board) {
        List<Tile> tiles = tileRepository.findByBoard(board);
        
        // 현재 보드에 이미 깔려 있는 미션 ID들을 가져옴
        Set<Long> currentMissionIds = tiles.stream()
                .map(tile -> tile.getMission().getMissionId())
                .collect(Collectors.toSet());

        // DB 1회 조회 후, 현재 사용 중이지 않은 미션들만 필터링
        List<Mission> availableMissions = missionRepository.findAll().stream()
                .filter(m -> !currentMissionIds.contains(m.getMissionId()))
                .collect(Collectors.toList());
        
        Collections.shuffle(availableMissions); // 남은 미션들을 무작위로 섞음

        int missionIndex = 0;
        for (Tile tile : tiles) {
            if (tile.getState().equals(TileState.CLOSE) && missionIndex < availableMissions.size()) {
                // 섞인 가용 미션 리스트에서 순차적으로 꺼내서 교체
                Mission newMission = availableMissions.get(missionIndex++);
                tile.changeMission(newMission);
                tile.changeState(TileState.OPEN);
            }
        }
    }

    /**
     * 보드 상태 조회
     */
    @Transactional(readOnly = true)
    public BoardDTO getBoard(Long memberId) {
        Board board = boardRepository.findByMember_MemberId(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_BOARD));

        List<BoardDTO.TileDTO> tileDTOs = convertTilesToDTOs(board.getTiles(), memberId);
        return new BoardDTO(board.getId(), board.getMemberPosition(), tileDTOs);
    }

    /**
     * 주사위 이동 및 미션 갱신 처리
     */
    @Transactional
    public BoardDTO moveMember(Long boardId, Integer dice, boolean isCycle) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_BOARD));

        board.moveMemberPosition(dice);

        if (isCycle) {
            updateMissionsOnBoard(board);
        }

        List<BoardDTO.TileDTO> tileDTOs = convertTilesToDTOs(board.getTiles(), board.getMember().getMemberId());
        return new BoardDTO(board.getId(), board.getMemberPosition(), tileDTOs);
    }

    /**
     * 타일들을 DTO로 변환
     */
    @Transactional(readOnly = true)
    public List<BoardDTO.TileDTO> convertTilesToDTOs(List<Tile> tiles, Long memberId) {
        return tiles.stream().map(tile -> {
            Optional<MissionSummit> summitOpt = missionSummitRepository
                    .findTopByMemberIdAndMissionIdOrderByCreatedAtDesc(memberId, tile.getMission().getMissionId());

            MissionSummitState missionSummitState = summitOpt.map(MissionSummit::getState).orElse(null);
            String rejectionReason = summitOpt.map(MissionSummit::getRejection).orElse(null);
            String categoryName = tile.getMission().getMissionCategory().getName();

            return new BoardDTO.TileDTO(
                    tile.getId(),
                    tile.getOrder(),
                    tile.getState(),
                    new BoardDTO.MissionDTO(
                            tile.getMission().getMissionId(),
                            tile.getMission().getTitle(),
                            tile.getMission().getContent(),
                            tile.getMission().getImageUrl(),
                            categoryName,
                            missionSummitState,
                            rejectionReason
                    )
            );
        }).collect(Collectors.toList());
    }
}
