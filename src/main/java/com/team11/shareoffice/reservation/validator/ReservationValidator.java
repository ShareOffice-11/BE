package com.team11.shareoffice.reservation.validator;

import com.team11.shareoffice.global.exception.CustomException;
import com.team11.shareoffice.global.util.ErrorCode;
import com.team11.shareoffice.member.entity.Member;
import com.team11.shareoffice.post.entity.Post;
import com.team11.shareoffice.post.repository.PostRepository;
import com.team11.shareoffice.reservation.dto.ReservationRequestDto;
import com.team11.shareoffice.reservation.entity.Reservation;
import com.team11.shareoffice.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationValidator {

    private final PostRepository postRepository;
    private final ReservationRepository reservationRepository;

    public Post validateIsExistPost(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_POST));
    }

    public Reservation validateIsExistReservation(Long id) {
        return reservationRepository.findById(id).orElseThrow( () -> new CustomException(ErrorCode.NOT_EXIST_RESERVATION));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Reservation validateReserveDate(Post post, Member member, ReservationRequestDto requestDto){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(formatter);
        LocalDate todayDate = LocalDate.parse(today, formatter);
        LocalDate reservationStartDate = requestDto.getStartDate();

        int comparison = todayDate.compareTo(reservationStartDate);

        if (comparison > 0) {
            throw new CustomException(ErrorCode.INVALID_DATE);
        }

        List<Reservation> reservationList = reservationRepository.findAllByPostReservedAndNotFinished(post, requestDto.getStartDate(), requestDto.getEndDate());
        if(!reservationList.isEmpty()){
            throw new CustomException(ErrorCode.EXIST_RESERVE_DATE);
        }
        Reservation newReserve = new Reservation(member, post, requestDto.getStartDate(), requestDto.getEndDate());
        reservationRepository.save(newReserve);
        return newReserve;
    }

    public void validateReservation(Post post, Reservation reservation, Member member){

        if(!(reservation.getMember().getId().equals(member.getId()))){
            throw new CustomException(ErrorCode.NOT_RESERVED_MEMBER);
        }
        else if(!(reservation.getPost().getId().equals(post.getId()))){
            throw new CustomException(ErrorCode.NOT_EXIST_RESERVATION);
        }
    }

}
