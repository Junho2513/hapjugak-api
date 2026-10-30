package com.example.hapjugakapi;

import com.example.hapjugakapi.domain.Member;
import com.example.hapjugakapi.domain.Part;
import com.example.hapjugakapi.domain.Slot;
import com.example.hapjugakapi.domain.Song;
import com.example.hapjugakapi.repository.MemberRepository;
import com.example.hapjugakapi.repository.SlotRepository;
import com.example.hapjugakapi.repository.SongRepository;
import com.example.hapjugakapi.service.SongService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SlotConcurrencyTest {

    @Autowired
    private SongService songService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Test
    @DisplayName("10명이 동시에 같은 파트를 신청해도 1명만 성공한다")
    void 동시_파트_신청() throws InterruptedException {
        // given — 곡 하나와 드럼 자리 하나를 만든다
        Member proposer = memberRepository.save(newMember("제안자"));

        Song song = new Song("동시성 테스트곡", "테스터", null, null, proposer);
        song.addSlot(Part.DRUM);
        Song saved = songRepository.save(song);

        Long songId = saved.getId();
        Long slotId = saved.getSlots().get(0).getId();

        // 신청할 회원 10명
        int threadCount = 10;
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            memberIds.add(memberRepository.save(newMember("회원" + i)).getId());
        }

        // when — 10명이 동시에 같은 자리를 신청한다
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyToStart = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        for (Long memberId : memberIds) {
            executor.submit(() -> {
                try {
                    readyToStart.await();
                    songService.claimSlot(songId, slotId, memberId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        readyToStart.countDown();
        done.await();
        executor.shutdown();

        // then — 정확히 1명만 성공해야 한다
        System.out.println("성공 " + successCount.get() + "건, 실패 " + failCount.get() + "건");

        assertEquals(1, successCount.get(), "한 자리에 한 명만 들어가야 합니다");
        assertEquals(threadCount - 1, failCount.get());

        Slot slot = slotRepository.findById(slotId).orElseThrow();
        assertTrue(slot.isTaken());
    }

    private Member newMember(String nickname) {
        String email = UUID.randomUUID() + "@test.com";
        return new Member(email, nickname, "서울", "");
    }
}