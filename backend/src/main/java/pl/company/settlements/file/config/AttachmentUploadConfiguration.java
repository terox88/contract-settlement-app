package pl.company.settlements.file.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:attachment-upload.properties")
public class AttachmentUploadConfiguration { }
