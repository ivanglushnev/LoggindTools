# Инструкция по настройке логирования с использованием Log4j2 в проекте на Spring Boot

Часто возникает задача настроить логирование в проекте, при этом на локальном окружении логи должны выводиться в консоль 
и в файл для удобства в процессе отладки, а на удаленном сервере выводить логи в консоль крайне не желательно, а также 
помимо логирования в файл надо еще писать их в logstash или в БД.
А если у вас локально Windows, а на удаленной машине Linux, то отличается и адрес этого самого файла с логами.  
Таким образом возникает немало ситуаций, в которых нужно руками постоянно править конфигурацию системы логирования в 
зависимости от внешних обстоятельств. 

Постоянно помнить и комментировать аппендеры в своё время мне надоело и я выработал способ настройки Log4j2 так, 
чтобы в зависимости от выбранного Maven-профиля автоматически включались только нужные аппендеры.
Ниже приведена инструкция по настройке логирования с помощью Log4j2, 
результатом выполнения которой будет настроенная система логирования и два аппендера: CONSOLE и SOCKET.

### В первую очередь внесём изменения в конфигурацию Maven (pom.xml):

**В переменные на уровне всего `pom.xml` добавляем адрес хоста для logstash-аппендера:**

	<properties>
		<logstash.host>logstashcsm.example.ru</logstash.host>
	</properties>
	

**Добавляем зависимости:**

	<dependencies>    
		<dependency>    
			<groupId>org.springframework.boot</groupId>
	 		<artifactId>spring-boot-starter-web</artifactId>
	 		<exclusions>
	 			<exclusion>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-logging</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-log4j2</artifactId>
		</dependency>
	</dependencies>

Обратите внимание, что из `spring-boot-starter-web` мы исключаем зависимость `spring-boot-starter-logging`.

**Настроим Maven-профили, чтобы динамически управлять подключенными аппендерами. 
На уровне каждого профиля задаем переменные** `logstash.port`, `logger.console.tresholdFilter`, `logger.socket.tresholdFilter`.


    <profiles>
        <profile>
            <id>local</id>
            <properties>
                <logstash.port>10000</logstash.port>
                <logger.console.tresholdFilter>ALL</logger.console.tresholdFilter>
                <logger.socket.tresholdFilter>OFF</logger.socket.tresholdFilter>
            </properties>
        </profile>

        <profile>
            <id>dev</id>
            <properties>
                <logstash.port>10001</logstash.port>
                <logger.console.tresholdFilter>OFF</logger.console.tresholdFilter>
                <logger.socket.tresholdFilter>ALL</logger.socket.tresholdFilter>
            </properties>
        </profile>
    </profiles>
    
	
* `logstash.port` - порт, на который нужно отправлять логи.
* `logger.console.tresholdFilter` - значение задаёт уровень фильтрации логов, выводимых на консоль. В нашем случае `ALL` означает, что лог-записи всех уровней будут выводиться в консольный аппендер.
* `logger.socket.tresholdFilter` - значение задает уровень фильтрации логов, которые отправляются в logstash. `OFF` - означает, что никакие записи отправлены в этот аппендер не пройдут.


### Теперь нам необходимо внести изменения в `application.properties`, чтобы из файла `Log4j2.xml` можно было получить доступ к значению переменных, указанных в `pom.xml`:

* `logstash.host=@logstash.host@`
* `logstash.port=@logstash.port@`
* `logger.console.tresholdFilter=@logger.console.tresholdFilter@`
* `logger.socket.tresholdFilter=@logger.socket.tresholdFilter@`
	
### И, наконец, настраиваем конфигурацию самого Log4j2 в файле `log4j2.xml`:

	<?xml version="1.0" encoding="UTF-8"?>
	<Configuration>
		<Properties>
			<Property name="socket.host">${bundle:application:logstash.host}</Property>
			<Property name="socket.port">${bundle:application:logstash.port}</Property>
			<Property name="console.thresholdFilter">${bundle:application:logger.console.tresholdFilter}</Property>
			<Property name="socket.thresholdFilter">${bundle:application:logger.socket.tresholdFilter}</Property>
		</Properties>
		<Appenders>
			<Console name="CONSOLE" target="SYSTEM_OUT">
				<ThresholdFilter level="${console.thresholdFilter}"/>
				<PatternLayout pattern="%d %-5p [%t] %c{10} - %m%n"/>
			</Console>
			<Socket name="SOCKET" host="${socket.host}" port="${socket.port}" immediateFlush="true">
				<ThresholdFilter level="${socket.thresholdFilter}"/>
				<JSONLayout eventEol="true" compact="true"/>
			</Socket>
			<Async name="ASYNC">
				<AppenderRef ref="CONSOLE"/>
				<AppenderRef ref="SOCKET"/>
			</Async>
		</Appenders>
		
		<Loggers>
			<Logger name="ru.example" level="debug" additivity="false">
				<AppenderRef ref="ASYNC"/>
			</Logger>
			<Root level="error">
				<AppenderRef ref="ASYNC"/>
			</Root>
		</Loggers>
	</Configuration>

Вот и всё. Теперь логирование будет работать с учетом Maven-профиля, активного в проекте.
Обратите внимание, что в настройке определено два логера - `ru.example` и `root`, и у них разные уровни логирования событий. 
Первый будет работать на все события, порожденные классами из пакета `ru.example.*` и логировать все от уровня `DEBUG`, а `root` логер будет фиксировать все события, но с уровня `ERROR`. 
При этом, чтобы записи не дублировались в логах, используется настройка `additivity="false"`.
