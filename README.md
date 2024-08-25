![image](https://github.com/user-attachments/assets/d52d618e-d448-4bfe-9769-d06e7c9a6952)

---

## Spring Security Filter 동작 원리

Spring Security 는 클라이언트의 요청이 여러개의 filter 를 거쳐 ***DispatcherServlet(Controller)으로 향하는 중간 filter*** 에서 요청을 가로챈 후 검증(인증/인가)을 진행한다.

+ **클라이언트 요청 → Servlet Filter → Servlet (Controller)**

  ![image](https://github.com/user-attachments/assets/b3c68fca-13ea-4f63-8b75-0b77ec55b695)

<br>

+ **Delegating Filter Proxy**

  **`Servlet Container (Tomcat)`** 에 존재하는 FilterChain 에 DelegatingFilter 를 등록한 뒤 모든 요청을 가로챈다.

  ![image](https://github.com/user-attachments/assets/88e92e0d-dcfd-4e34-9567-4c98c8720c2e)

  > + ***DelegatingFilterProxy FilterChainProxy 모식도***
  >   
  > ![image](https://github.com/user-attachments/assets/87e40ac9-518d-431b-946e-c29ea10c06d3)
  
  > + ***FilterChainProxy***
  > 
  > ![image](https://github.com/user-attachments/assets/797c6b7c-be05-4851-9df5-de1f0deed667)
  

<br>

+ **Servlet FilterChain 의 DelegatingFilter → SecurityFilterChain (내부 처리 후) → Servlet FilterChain 의 DelegatingFilter**

  가로챈 요청은 SecurityFilterChain에서 처리 후 상황에 따른 거부, 리디렉션, servlet 으로 요청 전달을 진행한다.

  ![image](https://github.com/user-attachments/assets/8ab9f830-d7af-47b8-a7dc-c1e2a8c397fa)

<br>

+ **SecurityFilterChain의 필터 목록과 순서**

  ![image](https://github.com/user-attachments/assets/8983c726-6986-44b3-ac91-c1bc557f90d3)

<br>


## Form 로그인 방식에서 UsernamePasswordAuthenticationFilter

Form 로그인 방식에서는 클라이언트단이 username과 password를 전송한 뒤 SecurityFilter 를 통과하는데 **`UsernamePasswordAuthentication Filter`** 에서 회원 검증을 진행을 시작한다.

> 이 filter 가 등록되는 목적은 `POST : “/login”` 경로에서 Form 기반 인증을 진행할 수 있도록 multipart/form-data 형태의 username/password 데이터를 받아 ***인증 클래스에게 값을 넘겨주는 역할을 수행한다.***

(회원 검증의 경우 ***UsernamePasswordAuthenticationFilter가 호출한 AuthenticationManager*** 를 통해 진행하며 **DB에서 조회한 데이터를 UserDetailsService** 를 통해 받음)

우리의 JWT 프로젝트는 SecurityConfig 에서 formLogin 방식을 `disable` 했기 때문에 기본적으로 활성화 되어 있는 해당 필터는 동작하지 않는다.

![image](https://github.com/user-attachments/assets/11664e06-0c26-4a25-bd45-02ceb94070f2)

따라서 로그인을 진행하기 위해서 필터를 custom 하여 등록해야 한다.

<br>


### UsernamePasswordAuthenticationFilter에서 attemptAuthentication() 메소드

```java
@Override
public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
		
	// 로그인 경로 요청인지 확인
	if (this.postOnly && !request.getMethod().equals("POST")) {
		throw new AuthenticationServiceException("Authentication method not supported: " + request.getMethod());
	}
	
	// 요청으로부터 multipart/form-data로 전송되는 username, password 획득
	String username = obtainUsername(request);
	username = (username != null) ? username.trim() : "";
	String password = obtainPassword(request);
	password = (password != null) ? password : "";
	
	// 인증을 위해 위 데이터를 인증 토큰에 넣음
	UsernamePasswordAuthenticationToken authRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, password);

	// Allow subclasses to set the "details" property
	setDetails(request, authRequest);
	
	// username/password 기반 인증을 진행하는 AuthenticationManager에게 인증을 요청 후 응답
	return this.getAuthenticationManager().authenticate(authRequest);
}
```

위 **`attemptAuthentication 메소드에서 user 가 보낸 정보`** 를 받아 **AuthenticationManager** 에게 넘기는데 해당 class 들은 어떻게 구성되어 있고 어떤 과정을 거쳐서 로그인이 수행될까?

username/password 기반으로 국한되어 살펴보면 아래와 같다.

![image](https://github.com/user-attachments/assets/94ba49d7-34ff-4547-804e-c9d466d4fea4)


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
 
+ 서버측에서는 Refresh Token을 받을 endpoint(Controller) 를 구성하여 Refresh를 검증하고 Access를 응답한다.
 
<br>


## 4. Refresh Token이 탈취되는 경우

단일 → 다중 Token으로 전환하며 자주 사용되는 Access Token이 탈취되더라도 생명주기가 짧아 피해 확률이 줄었다.

하지만 Refresh Token 또한 사용되는 빈도만 적을뿐 탈취될 수 있는 확률이 존재한다. 

따라서 Refresh Token에 대한 보호 방법도 필요하다.

<br>

+ **Access/Refresh Token의 저장 위치 고려**

Local/Session Storage 및 Cookie 에 따라 XSS, CSRF 공격의 여부가 결정되기 때문에 각 Token 사용처에 알맞은 저장소 설정.

+ **Refresh Token Rotate**

Access Token을 갱신하기 위한 Refresh Token 요청 시 서버측에서에서 Refresh Token도 재발급을 진행하여 한 번 사용한 Refresh Token은 재사용하지 못하도록 한다.

<br>

## 5. Access/Refresh Token 저장 위치

클라이언트에서 발급 받은 JWT를 저장하기 위해 Local Storage 와 Cookie 에 대해 많은 고려를 한다. 각 storage 에 따른 특징과 취약점은 아래와 같다.

<br>

+ **`Local Storage`** : XSS 공격에 취약함 : Access Token 저장

+ **`httpOnly cookie`** : CSRF 공격에 취약함 : Refresh Token 저장

(위와 같은 설정은 필수적이지 않습니다. 주관적인 판단에 따라 편하신대로 커스텀하면 됩니다.)

<br> 

+ **고려**
  
  JWT 의 탈취는 보통 `XSS 공격` 으로 **Local storage 에 저장된 JWT** 를 가져갑니다.

  그럼 Cookie 방식으로 저장하면 안전하지 않을까라는 의문이 들지만, *Cookie 방식은 CSRF 공격에 취약합니다.* 그럼 각 상황에 알맞게 저장소를 선택합시다.

<br> 

+ **Access Token**

  Access Token은 주로 Local storage 에 저장됩니다.

  짧은 생명 주기로 탈취에서 사용까지 기간이 매우 짧고, 에디터 및 업로더에서 XSS를 방어하는 로직을 작성하여 최대한 보호 할 수 있지만 CSRF 공격의 경우 클릭 한 번으로 단시간에 요청이 진행되기 때문입니다.

  권한이 필요한 모든 경로에 사용되기 때문에 CSRF 공격의 위험보다는 XSS 공격을 받는 게 더 나은 선택일 수 있습니다.

<br>

+ **Refresh Token**
  
  Refresh Token은 주로 Cookie 에 저장됩니다.

  Cookie 는 XSS 공격을 받을 수 있지만 **`httpOnly`** 를 설정하면 완벽히 방어할 수 있습니다.

  그럼 가장 중요한 CSRF 공격에 대해 위험하지 않을까라는 의구심이 생깁니다.

  하지만 Refresh Token의 사용처는 *단 하나인 Token 재발급 경로입니다.*

  CSRF 는 Access Token이 접근하는 회원 정보 수정, 게시글 CRUD 에 취약하지만 Token 재발급 경로에서는 크게 피해를 입힐 만한 로직이 없기 때문입니다.

<br>

## 6. Refresh Token Rotate

위와 같이 저장소의 특징에 알맞은 JWT 보호 방법을 수행해도 탈취 당할 수 있는게 Web 세상입니다. 

따라서 생명주기가 긴 Refresh Token 에 대한 추가적인 방어 조치가 있습니다.

Access Token 이 만료되어 Refresh Token 을 가지고 서버 특정 엔드포인트에 재발급을 진행하면 Refresh Token 또한 재발급하여 프론트측으로 응답하는 방식이 **Refresh Rotate** 입니다.

<br> 

## 7. 로그아웃과 Refresh Token 주도권

+ **문제**
  
  Logout 을 구현하면 프론트측에 존재하는 Access/Refresh Token 을 제거합니다.

  그럼 프론트측에서 요청을 보낼 JWT 가 없기 때문에 logout 이 되었다고 생각하지만 이미 해커가 JWT 를 복제 했다면 요청이 수행됩니다.

  위와 같은 문제가 존재하는 이유는 단순하게 JWT 를 발급해준 순간 server 측의 주도권은 없기 때문입니다.

  (세션 방식은 상태를 STATE하게 관리하기 때문에 주도권이 server 측에 있음)

   Logout 케이스뿐만 아니라 JWT 가 탈취되었을 경우 server 측 주도권이 없기 때문에 피해를 막을 방법은 생명주기가 끝이나 길 기다리는 방법입니다.

<br>

+ **방어 방법**
  
  위 문제의 해결법은 생명주기가 긴 Refresh Token은 발급과 함께 server측 저장소에도 저장하여 요청이 올때마다 저장소에 존재하는지 확인하는 방법으로 server측에서 주도권을 가질 수 있습니다.

  만약 logout 을 진행하거나 탈취에 의해 피해가 진행되는 경우 server측 저장소에서 해당 JWT 를 삭제하여 피해를 방어할 수 있습니다.

  (Refresh Token Blacklisting 이라고도 부릅니다.)

<br>


![image](https://github.com/user-attachments/assets/19bd9f6a-8041-4263-a10d-46d68a290add)

Server측 JWTFilter 에서 Access Token 의 만료로 인한 특정한 상태 코드가 응답되면 프론트측 Axios Interceptor 와 같은 예외 핸들러에서 Access token 재발급을 위한 Refresh 를 server 측으로 전송한다.

이때 Server 에서는 Refresh Token 을 받아 새로운 Access Token 을 응답하는 코드를 작성하면 된다.


---


## Spring Security 내부 구조 중 SecurityContextHolder

### SecurityFilterChain filter별 작업 상태 저장

+ **상태 저장 필요**

![image](https://github.com/user-attachments/assets/9fd03b18-d975-4c8a-a225-27cd765e0c06)

SecurityFilterChain 내부에 존재하는 각각의 filter가 security 관련 작업을 진행한다.

모든 작업은 기능 단위로 분업하여 진행함으로 앞에서 한 작업을 *뒤 filter* 가 알기 위한 저장소 개념이 필요하다.

<br>

예를 들어, 인가 filter 가 작업을 하려면 User 의 ROLE 정보가 필요한데, **`앞단의 filter 에서 user 에게 ROLE 값을 부여한 결과`** 를 *인가 filter 까지 공유해야* 확인할 수 있다.

<br>

+ **저장 : Authentication 객체**

![image](https://github.com/user-attachments/assets/eec97ef7-5a0a-49a7-a3a1-017420615d33)

해당하는 정보가 Authentication 이라는 객체에 담긴다.

(이 객체에 id, login 여부, ROLE 데이터가 담긴다.)

Authentication 객체는 SecurityContext 에 포함되어 관리되며 *SecurityContext 는 0개 이상 존재* 할 수 있다.

그리고 이 `N개의 SecurityContext` 는 **하나의 SecurityContextHolder** 에 의해서 관리된다.

<br>

  + **Authentication 객체**

    + Principal : User 에 대한 정보
   
    + Credentials : 증명 (Password, Token)
   
    + Authorities : User 의 권한(ROLE) 목록

<br>

  + **접근**

    ```java
    SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    ```

    SecurityContextHolder 의 method 는 static 으로 선언되기 때문에 어디서든 접근할 수 있다.

<br>

  + **SecurityContextHolder**

    ```java
    public class SecurityContextHolder {

    }
    ```

<br>

  + **특이 사항**

    다수의 사용자인 Multi thread 환경에서 SecurityContextHolder 를 통해 SecurityContext 를 부여하는 관리 전략은 ***위임하여 다른 class 에게 맡긴다.***

    (사용자별로 다른 저장소를 제공해야 인증 정보가 겹치치 일이 발생하지 않는다.)

    즉, SecurityContextHolder 는 SecurityContext 들을 관리하는 method 들을 제공하지만 실제로 등록, 초기화, 읽기와 같은 작업은 **`SecurityContextHolderStrategy interface`** 를 활용한다.

<br>

  + **SecurityContextHolderStrategy 구현 종류**

    ```java
    private static void initializeStrategy() {

	      if (MODE_PRE_INITIALIZED.equals(strategyName)) {
		        Assert.state(strategy != null, "When using " + MODE_PRE_INITIALIZED
				        + ", setContextHolderStrategy must be called with the fully constructed strategy");
		        return;
	      }
	      if (!StringUtils.hasText(strategyName)) {
		      // Set default
		      strategyName = MODE_THREADLOCAL;
	      }
	      if (strategyName.equals(MODE_THREADLOCAL)) {
		      strategy = new ThreadLocalSecurityContextHolderStrategy();
		      return;
	      }
	      if (strategyName.equals(MODE_INHERITABLETHREADLOCAL)) {
		      strategy = new InheritableThreadLocalSecurityContextHolderStrategy();
		      return;
	      }
	      if (strategyName.equals(MODE_GLOBAL)) {
		      strategy = new GlobalSecurityContextHolderStrategy();
		      return;
	      }
	      // Try to load a custom strategy
	      try {
		        Class<?> clazz = Class.forName(strategyName);
		        Constructor<?> customStrategy = clazz.getConstructor();
		        strategy = (SecurityContextHolderStrategy) customStrategy.newInstance();
	       }
	      catch (Exception ex) {
		        ReflectionUtils.handleReflectionException(ex);
	       }
      }
    ```

<br>

기본적으로 **`threadlocal`** 방식을 사용한다. 

<br>


### ThreadLocal 방식에서 SecurityContext

+ **ThreadLocalSecurityContextHolderStrategy**

  ```java
  final class ThreadLocalSecurityContextHolderStrategy implements SecurityContextHolderStrategy {

	  private static final ThreadLocal<Supplier<SecurityContext>> contextHolder = new ThreadLocal<>();

  }
  ```

<br>

+ **접근 thread 별 SecurityContext 배분**

  `Tomcat WAS` 는 Multi thread 방식으로 동작한다.

  User 가 접속하면 *user 에게 하나의 thread* 를 할당한다.

  각각의 user 는 **동시에 Security login logic** 을 사용할 수 있다.

<br>

  이때 SecurityContextHolder 의 필드에 선언된 SecurityContext 를 호출하게 된다면 thread 간 공유하는 memory 의 code 영역에 데이터가 있기 때문에 정보가 덮어지는 현상이 발생한다고 생각할 수 있는데,

  **threadLocal 로 관리되기 때문에 thread 별로 다른 구획을 나눠 제공한다.**

![image](https://github.com/user-attachments/assets/8989f995-f9f0-4498-9ef1-4f4df316f658)

<br>

### 요약

+ SecurityFilterChain 의 각각의 filter 에서 수행한 작업 내용이 전달되기 위해 요청(user) 별로 Authentication 객체를 할당하여 확인함.

+ Authentication 객체는 SecurityContextHolder 의 SecurityContext 가 관리함.

+ Multi thread 환경에서 SecurityContext 를 만들고 필드의 static 영역에 선언된 SecurityContext 를 다루는 전략은 기본적으로 thraedLocal 전략을 이용함.

<br>

***어디에서 사용하는가 ?***

+ `Logout Filter` : logout 로직을 수행하면서 SecurityContext 의 Authentication 객체를 비움

+ `Login Filter` : 인증을 완료한 뒤 User 정보를 담은 Authentication 객채를 넣음

<br>

***추가***

+ **SecurityContextHolder 는 왜 Bean 이 아닌 static 으로 등록할까 ?**

  Util class 의 경우 static 으로 선언하는게 암묵적인 rule.

+ **Thread safe 한 함수의 local 변수 대신 왜 field 변수를 사용하고 threadLocal 을 사용하는지?**

  Thread 단위로 기억을 하는 경우는 field 변수를 사용함.


<br>

---



[고민했던 부분에 대한 좋은 issue article] [https://github.com/boojongmin/memo/issues/7]
