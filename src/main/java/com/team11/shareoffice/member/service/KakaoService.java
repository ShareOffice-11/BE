package com.team11.shareoffice.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team11.shareoffice.global.jwt.CookieUtil;
import com.team11.shareoffice.global.jwt.JwtUtil;
import com.team11.shareoffice.global.jwt.dto.TokenDto;
import com.team11.shareoffice.global.service.RedisService;
import com.team11.shareoffice.member.dto.UserInfoDto;
import com.team11.shareoffice.member.entity.Member;
import com.team11.shareoffice.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class KakaoService {
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;
    private final RedisService redisService;
    private final CookieUtil cookieUtil;


    @Value("${kakao.client.secret}")
    private String clientSecret;

    public TokenDto kakaoLogin(String code, HttpServletResponse response) throws JsonProcessingException {

        // 1. "인가 코드"로 "액세스 토큰" 요청
        String accessToken = getToken(code);
        // 2. 토큰으로 카카오 API 호출 : "액세스 토큰"으로 "카카오 사용자 정보" 가져오기
        UserInfoDto userInfo = fetchKakaoUserInfo(accessToken);
        // 3. 필요 시에 회원 가입
        Member kakaoUser = registerKakaoUserIfNeeded(userInfo);

        return new TokenDto(issueTokens(response, kakaoUser.getEmail()),null);
    }

    public String issueTokens(HttpServletResponse response, String email){
        TokenDto tokenDto = jwtUtil.createAllToken(email);
//        response.addHeader(JwtUtil.ACCESS_TOKEN, tokenDto.getAccessToken());
        cookieUtil.createCookie(response, tokenDto.getRefreshToken());
        redisService.setValues(email, tokenDto.getRefreshToken(), Duration.ofDays(1));
        return tokenDto.getAccessToken();
    }

    private String getToken(String code) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        String url = UriComponentsBuilder.fromHttpUrl("https://kauth.kakao.com/oauth/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", "a2e918a8313b1ec2a828afcfa8e8991b")
                //.queryParam("redirect_uri", "http://localhost:3000/oauth/kakao")
                .queryParam("redirect_uri", "https://ohpick.shop/oauth/kakao","http://localhost:3000/oauth/kakao")
                .queryParam("Client_Secret", clientSecret)
                .queryParam("code", code)
                .toUriString();

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        return jsonNode.get("access_token").asText();
    }

    /////////////
    private UserInfoDto fetchKakaoUserInfo(String accessToken) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<?> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        String url = "https://kapi.kakao.com/v2/user/me";

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        Long id = jsonNode.get("id").asLong();
        String email =jsonNode.get("kakao_account").get("email").asText();
        String nickname = jsonNode.get("properties").get("nickname").asText();
        String imageUrl = jsonNode.get("kakao_account").get("profile").get("profile_image_url").asText();

        log.info("카카오 사용자 정보: " + id);
        return new UserInfoDto(id,nickname,email,imageUrl);
    }

    //////////////////////////////////////////////////////////////
    private Member registerKakaoUserIfNeeded(UserInfoDto userInfo) {
        // DB에 중복된 kakaoId 있는지 확인
        Member kakaomember = memberRepository.findByKakaoId(userInfo.getKakaoId()).orElse(null);

        if (kakaomember == null) {
            // 카카오 사용자 email 동일한 email 가진 회원이 있는지 확인
            String kakaoEmail = userInfo.getEmail();
            Member sameEmailUser = memberRepository.findByEmail(kakaoEmail).orElse(null);
            if (sameEmailUser != null) {
                kakaomember = sameEmailUser;
                // 기존 회원정보에 카카오 Id 추가
                kakaomember = kakaomember.kakaoIdAndImageUpdate(userInfo.getKakaoId(),userInfo.getImageUrl());

            } else {
                // 신규 회원가입
                // password: random UUID
                String password = "kakao_" + kakaoEmail;
                String encodedPassword = passwordEncoder.encode(password);

                kakaomember = new Member(userInfo.getEmail(), userInfo.getKakaoId(), encodedPassword, userInfo.getNickname(), userInfo.getImageUrl());
            }

            memberRepository.save(kakaomember);

        }
        kakaomember = kakaomember.kakaoImageUpdate(userInfo.getImageUrl());
        return kakaomember;
    }

}
