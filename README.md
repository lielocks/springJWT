![image](https://github.com/user-attachments/assets/d52d618e-d448-4bfe-9769-d06e7c9a6952)

---

## Spring Security Filter 동작 원리

Spring Security 는 클라이언트의 요청이 여러개의 filter 를 거쳐 DispatcherServlet(Controller)으로 향하는 중간 filter 에서 요청을 가로챈 후 검증(인증/인가)을 진행한다.

+ **클라이언트 요청 → Servlet Filter → Servlet (Controller)**

  ![image](https://github.com/user-attachments/assets/b3c68fca-13ea-4f63-8b75-0b77ec55b695)

<br>

+ **Delegating Filter Proxy**

  Servlet Container (Tomcat) 에 존재하는 FilterChain 에 DelegatingFilter 를 등록한 뒤 모든 요청을 가로챈다.

  ![image](https://github.com/user-attachments/assets/88e92e0d-dcfd-4e34-9567-4c98c8720c2e)

  > + ***DelegatingFilterProxy FilterChainProxy 모식도***
  >   
  > ![image](https://github.com/user-attachments/assets/87e40ac9-518d-431b-946e-c29ea10c06d3)
  
  > + ***FilterChainProxy***
  > 
  > ![image](https://github.com/user-attachments/assets/797c6b7c-be05-4851-9df5-de1f0deed667)
  

<br>

+ **Servlet FilterChain 의 DelegatingFilter → SecurityFilterChain (내부 처리 후) → Servlet FilterChain 의 DelegatingFilter**

  가로챈 요청은 SecurityFilterChain에서 처리 후 상황에 따른 거부, 리디렉션, 서블릿으로 요청 전달을 진행한다.

  ![image](https://github.com/user-attachments/assets/8ab9f830-d7af-47b8-a7dc-c1e2a8c397fa)

<br>

+ **SecurityFilterChain의 필터 목록과 순서**

  ![image](https://github.com/user-attachments/assets/8983c726-6986-44b3-ac91-c1bc557f90d3)

<br>


## Form 로그인 방식에서 UsernamePasswordAuthenticationFilter

Form 로그인 방식에서는 클라이언트단이 username과 password를 전송한 뒤 SecurityFilter 를 통과하는데 **`UsernamePasswordAuthentication Filter`** 에서 회원 검증을 진행을 시작한다.

(회원 검증의 경우 ***UsernamePasswordAuthenticationFilter가 호출한 AuthenticationManager*** 를 통해 진행하며 **DB에서 조회한 데이터를 UserDetailsService** 를 통해 받음)

우리의 JWT 프로젝트는 SecurityConfig 에서 formLogin 방식을 `disable` 했기 때문에 기본적으로 활성화 되어 있는 해당 필터는 동작하지 않는다.

따라서 로그인을 진행하기 위해서 필터를 custom 하여 등록해야 한다.

<br>


## 1. Token 사용 추적

“스프링 시큐리티 JWT” 시리즈를 통해 구현한 단일 Token의 사용처를 추적하면 아래와 같다.

+ 로그인 성공 JWT 발급 : 서버측 → 클라이언트로 JWT 발급

+ 권한이 필요한 모든 요청 : 클라이언트 → 서버측 JWT 전송
 
<br>

권한이 필요한 요청은 서비스에서 많이 발생한다. (회원 CRUD, 게시글/댓글 CRUD, 주문 서비스, 등등)

따라서 JWT는 매시간 수많은 요청을 위해 클라이언트의 JS 코드로 HTTP 통신을 통해 서버로 전달된다.

해커는 클라이언트 측에서 XSS를 이용하거나 HTTP 통신을 가로채서 Token을 훔칠 수 있기 때문에 여러 기술을 도입하여 탈취를 방지하고 탈취되었을 경우 대비 로직이 존재합니다.

<br>

## 2. 다중 Token : Refresh Token 과 생명 주기

위와 같은 문제가 발생하지 않도록 **`Access/Refresh Token`** 개념이 등장한다.

<br> 

자주 사용되는 Token의 생명주기는 짧게(약 10분), 이 Token이 만료되었을 때 함께 받은 Refresh Token(24시간 이상)으로 Token을 재발급.

(생명주기가 짧으면 만료시 매번 로그인을 진행하는 문제가 발생, 생명주기가 긴 Refresh도 함께 발급한다.)

<br>

### 1. 로그인 성공시 생명주기와 활용도가 다른 Token 2개 발급 : Access/Refresh

**`Access Token`** : 권한이 필요한 모든 **요청 header** 에 사용될 JWT로 탈취 위험을 낮추기 위해 약 10분 정도의 짧은 생명주기를 가진다.

**`Refresh Token`** : Access Token 이 만료되었을 때 **재발급 받기 위한 용도로만** 사용되며 약 24시간 이상의 긴 생명주기를 가진다.

<br>

### 2. 권한이 필요한 모든 요청 : Access Token 을 통해 요청

Access Token만 사용하여 요청하기 때문에 Refresh Token은 호출 및 전송을 빈도가 낮음.

<br> 

### 3. 권한이 알맞다는 가정하에 2가지 상황 : 데이터 응답, Token 만료 응답

<br>


### 4. Token이 만료된 경우 Refresh Token으로 Access Token 발급

Access Token이 만료되었다는 요청이 돌아왔을 경우 프론트엔드 로직에 의해 “1.” 에서 발급 받은 Refresh Token을 가지고 서버의 **`특정 경로(Refresh Token을 받는 경로)`** 에 요청을 보내어 **Access Token 을 재발급** 받는다.

<br> 

### 5. 서버측에서는 Refresh Token을 검증 후 Access Token을 새로 발급한다.

<br> 

## 3. 다중 Token 구현 포인트

+ 로그인이 완료되면 successHandler 에서 Access/Refresh Token 2개를 발급해 응답한다.

  각 Token은 각기 다른 생명주기, payload 정보를 가진다.

+ Access Token 요청을 검증하는 **`JWTFilter`** 에서 Access Token 이 만료된 경우는 프론트 개발자와 협의된 상태 코드와 메시지를 응답한다.
 
+ 프론트측 API 클라이언트 (axios, fetch) 요청시 Access Token 만료 요청이 오면 예외문을 통해 Refresh Token을 서버측으로 전송하고 Access Token을 발급 받는 로직을 수행한다. (기존 Access는 제거)
 
+ 서버측에서는 Refresh Token을 받을 엔드포인트 (컨트롤러)를 구성하여 Refresh를 검증하고 Access를 응답한다.
 
<br>


## 4. Refresh Token이 탈취되는 경우

단일 → 다중 Token으로 전환하며 자주 사용되는 Access Token이 탈취되더라도 생명주기가 짧아 피해 확률이 줄었다.

하지만 Refresh Token 또한 사용되는 빈도만 적을뿐 탈취될 수 있는 확률이 존재한다. 

따라서 Refresh Token에 대한 보호 방법도 필요하다.

<br>

+ **Access/Refresh Token의 저장 위치 고려**

로컬/세션 스토리지 및 쿠키에 따라 XSS, CSRF 공격의 여부가 결정되기 때문에 각 Token 사용처에 알맞은 저장소 설정.

+ **Refresh Token Rotate**

Access Token을 갱신하기 위한 Refresh Token 요청 시 서버측에서에서 Refresh Token도 재발급을 진행하여 한 번 사용한 Refresh Token은 재사용하지 못하도록 한다.

<br>

## 5. Access/Refresh Token 저장 위치

클라이언트에서 발급 받은 JWT를 저장하기 위해 로컬 스토리지와 쿠키에 대해 많은 고려를 한다. 각 스토리지에 따른 특징과 취약점은 아래와 같다.

<br>

+ **`Local Storage`** : XSS 공격에 취약함 : Access Token 저장

+ **`httpOnly cookie`** : CSRF 공격에 취약함 : Refresh Token 저장

(위와 같은 설정은 필수적이지 않습니다. 주관적인 판단에 따라 편하신대로 커스텀하면 됩니다.)

<br> 

+ **고려**
  
  JWT의 탈취는 보통 XSS 공격으로 로컬 스토리지에 저장된 JWT를 가져갑니다.

  그럼 쿠키 방식으로 저장하면 안전하지 않을까라는 의문이 들지만, 쿠키 방식은 CSRF 공격에 취약합니다. 그럼 각 상황에 알맞게 저장소를 선택합시다.

<br> 

+ **Access Token**

  Access Token은 주로 로컬 스토리지에 저장됩니다.

  짧은 생명 주기로 탈취에서 사용까지 기간이 매우 짧고, 에디터 및 업로더에서 XSS를 방어하는 로직을 작성하여 최대한 보호 할 수 있지만 CSRF 공격의 경우 클릭 한 번으로 단시간에 요청이 진행되기 때문입니다.

  권한이 필요한 모든 경로에 사용되기 때문에 CSRF 공격의 위험보다는 XSS 공격을 받는 게 더 나은 선택일 수 있습니다.

<br>

+ **Refresh Token**
  
  Refresh Token은 주로 쿠키에 저장됩니다.

  쿠키는 XSS 공격을 받을 수 있지만 httpOnly를 설정하면 완벽히 방어할 수 있습니다.

  그럼 가장 중요한 CSRF 공격에 대해 위험하지 않을까라는 의구심이 생깁니다.

  하지만 Refresh Token의 사용처는 단 하나인 Token 재발급 경로입니다.

  CSRF는 Access Token이 접근하는 회원 정보 수정, 게시글 CRUD에 취약하지만 Token 재발급 경로에서는 크게 피해를 입힐 만한 로직이 없기 때문입니다.

<br>

## 6. Refresh Token Rotate

위와 같이 저장소의 특징에 알맞은 JWT 보호 방법을 수행해도 탈취 당할 수 있는게 웹 세상입니다. 

따라서 생명주기가 긴 Refresh Token에 대한 추가적인 방어 조치가 있습니다.

Access Token이 만료되어 Refresh Token을 가지고 서버 특정 엔드포인트에 재발급을 진행하면 Refresh Token 또한 재발급하여 프론트측으로 응답하는 방식이 Refresh Rotate 입니다.

<br> 

## 7. 로그아웃과 Refresh Token 주도권

+ **문제**
  
  로그아웃을 구현하면 프론트측에 존재하는 Access/Refresh Token을 제거합니다.

  그럼 프론트측에서 요청을 보낼 JWT가 없기 때문에 로그아웃이 되었다고 생각하지만 이미 해커가 JWT를 복제 했다면 요청이 수행됩니다.

  위와 같은 문제가 존재하는 이유는 단순하게 JWT를 발급해준 순간 서버측의 주도권은 없기 때문입니다.

  (세션 방식은 상태를 STATE하게 관리하기 때문에 주도권이 서버측에 있음)

   로그아웃 케이스뿐만 아니라 JWT가 탈취되었을 경우 서버 측 주도권이 없기 때문에 피해를 막을 방법은 생명주기가 끝이나 길 기다리는 방법입니다.

<br>

+ **방어 방법**
  
  위 문제의 해결법은 생명주기가 긴 Refresh Token은 발급과 함께 서버측 저장소에도 저장하여 요청이 올때마다 저장소에 존재하는지 확인하는 방법으로 서버측에서 주도권을 가질 수 있습니다.

  만약 로그아웃을 진행하거나 탈취에 의해 피해가 진행되는 경우 서버측 저장소에서 해당 JWT를 삭제하여 피해를 방어할 수 있습니다.

  (Refresh Token 블랙리스팅이라고도 부릅니다.)

<br>


![image](https://github.com/user-attachments/assets/19bd9f6a-8041-4263-a10d-46d68a290add)

서버측 JWTFilter 에서 Access Token 의 만료로 인한 특정한 상태 코드가 응답되면 프론트측 Axios Interceptor와 같은 예외 핸들러에서 Access 토큰 재발급을 위한 Refresh 를 서버측으로 전송한다.

이때 서버에서는 Refresh Token 을 받아 새로운 Access Token 을 응답하는 코드를 작성하면 된다.
