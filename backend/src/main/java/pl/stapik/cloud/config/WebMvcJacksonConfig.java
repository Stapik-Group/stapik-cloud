package pl.stapik.cloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcJacksonConfig implements WebMvcConfigurer {

    private final Jackson2ObjectMapperBuilder springBuilder;

    public WebMvcJacksonConfig(Jackson2ObjectMapperBuilder springBuilder) {
        this.springBuilder = springBuilder;
    }

    @Bean
    public MappingJackson2HttpMessageConverter jackson2Converter() {
        ObjectMapper objectMapper = springBuilder
                .modulesToInstall(new JsonNullableModule())
                .build();
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter jackson2Converter = jackson2Converter();

        int index = -1;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof JacksonJsonHttpMessageConverter) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            converters.add(index, jackson2Converter);
        } else {
            converters.addFirst(jackson2Converter);
        }
    }
}