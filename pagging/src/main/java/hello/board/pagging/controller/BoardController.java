package hello.board.pagging.controller;

import hello.board.pagging.common.exception.BadRequestException;
import hello.board.pagging.domain.File;
import hello.board.pagging.model.FileStore;
import hello.board.pagging.model.board.*;
import hello.board.pagging.model.member.MemberDetailsDto;
import hello.board.pagging.repository.FileRepository;
import hello.board.pagging.service.BoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/board")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;
    private final FileStore fileStore;
    @Value("${file.maxSize}")
    private Integer fileMaxSize;

    /**
     * 게시글 보기 뷰
     */
    @GetMapping()
    public String boardForm(@ModelAttribute("params") final SearchDto params,
                            Model model) {
        PagingResponseDto<BoardDto> response = boardService.findAllBoard(params);
        model.addAttribute("response", response);
        return "board";
    }

    /**
     * 게시글 상세 보기 뷰
     */
    @GetMapping("/{boardId}")
    public String boardDetailForm(@AuthenticationPrincipal MemberDetailsDto memberDetailsDto,
                                  @PathVariable("boardId") Long boardId,
                                  Model model) {

        BoardDto boardDto = boardService.findBoardAndFiles(boardId);

        if(boardDto != null) {
            model.addAttribute("AuthMemberId", memberDetailsDto.getMember().getMemberId()); // 삭제 하기 위한 model 속성
            model.addAttribute("boardDto", boardDto);
            return "boardDetail";
        }

        return "redirect:/board";
    }

    /**
     * 게시글 쓰기 뷰
     */
    @GetMapping("/writeView")
    public String boardWriteFrom(@ModelAttribute BoardSaveDto boardSaveDto,
                                 Model model) {
        model.addAttribute("fileMaxSize", fileMaxSize);
        return "boardWrite";
    }

    /**
     * 게시글 작성 및 파일 업로드
     */
    @PostMapping("/write")
    public String boardWrite(@AuthenticationPrincipal MemberDetailsDto memberDetailsDto,
                             @Valid @ModelAttribute BoardSaveDto boardSaveDto,
                             BindingResult bindingResult,
                             Model model) {

        model.addAttribute("fileMaxSize", fileMaxSize);

        if(bindingResult.hasErrors()) {
            return "boardWrite";
        }

        if(!fileStore.isImageFiles(boardSaveDto.getImageFiles())) {
            // 파일이 존재하면서 이미지 파일 확장자(jpg, png, gif)가 아닌 경우 글로벌 오류 메세지
            bindingResult.reject("isNotImage", "이미지 파일은 jpg, png, gif 만 가능합니다.");
            return "boardWrite";
        }

        if(boardSaveDto.getImageFiles().size() > 3) {
            // 이미지 파일이 3개 이상일 경우 글로벌 오류 메세지
            bindingResult.reject("isManyImage", "이미지 파일은 최대 3개까지 가능합니다.");
            return "boardWrite";
        }

        boardService.createBoard(boardSaveDto, memberDetailsDto.getMember());

        return "redirect:/board";
    }

    /**
     * 게시글 수정 폼,
     * 수정할 수 없는 권한 언체크 예외 throw
     */
    @GetMapping("/modifyView/{boardId}")
    public String boardModifyForm(@AuthenticationPrincipal MemberDetailsDto memberDetailsDto,
                                  @ModelAttribute BoardModifyDto boardModifyDto,
                                  @PathVariable("boardId") Long boardId,
                                  Model model) {

        BoardDto boardAndFiles = boardService.findBoardAndFiles(boardId);

        // 게시글 글쓴이가 아닐 경우 Exception
        if(!boardAndFiles.getMemberId().equals(memberDetailsDto.getMember().getMemberId())) {
            throw new BadRequestException("수정할 수 없는 권한입니다.");
        }

        modifyViewModelAdd(model, boardAndFiles, boardModifyDto);

        return "boardModify";
    }

    /**
     * 게시글 수정
     */
    @PostMapping("/modify")
    public String boardModify(@AuthenticationPrincipal MemberDetailsDto memberDetailsDto,
                              @Valid @ModelAttribute BoardModifyDto boardModifyDto,
                              BindingResult bindingResult,
                              Model model) {

        BoardDto boardAndFiles = boardService.findBoardAndFiles(boardModifyDto.getBoardId());

        if(bindingResult.hasErrors()) {
            modifyViewModelAdd(model, boardAndFiles, boardModifyDto);
            return "boardModify";
        }

        if(!fileStore.isImageFiles(boardModifyDto.getImageFiles())){
            // 파일이 존재하면서 이미지 파일 확장자(jpg, png, gif)가 아닌 경우 글로벌 오류 메세지
            bindingResult.reject("isNotImage", "이미지 파일은 jpg, png, gif 만 가능합니다.");
            modifyViewModelAdd(model, boardAndFiles, boardModifyDto);
            return "boardModify";
        }

        boardService.updateBoard(boardModifyDto, memberDetailsDto.getMember());

        return "redirect:/board";
    }

    private void modifyViewModelAdd( Model model, BoardDto boardAndFiles, BoardModifyDto boardModifyDto) {
        boardModifyDto.setBoardId(boardAndFiles.getBoardId());
        boardModifyDto.setBoardTitle(boardAndFiles.getBoardTitle());
        boardModifyDto.setBoardContent(boardAndFiles.getBoardContent());

        List<File> fileList = null;
        if(!ObjectUtils.isEmpty(boardAndFiles.getFileList())) {
            fileList = boardAndFiles.getFileList();
        }

        model.addAttribute("fileList", fileList);
        model.addAttribute("fileMaxSize", fileMaxSize);
    }

    /**
     * 게시글 삭제
     */
    @PostMapping("/remove")
    public String boardRemove(@AuthenticationPrincipal MemberDetailsDto memberDetailsDto,
                              @ModelAttribute BoardDeleteDto boardDeleteDto) {
        boardService.deleteSetupBoard(boardDeleteDto.getBoardId(), memberDetailsDto.getMember());

        return "redirect:/board";
    }
}
