package com.sentinel.aml.ingestion_service.config;

import com.sentinel.aml.ingestion_service.batch.CsvSkipListener;
import com.sentinel.aml.ingestion_service.batch.TransactionItemProcessor;
import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.repository.OutboxEventRepository;
import com.sentinel.aml.ingestion_service.repository.TransactionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    @Bean
    @StepScope
    public FlatFileItemReader<TransactionCsvDto> csvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        return new org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder<TransactionCsvDto>()
                .name("transactionCsvReader")
                .resource(new org.springframework.core.io.FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .names("sourceAccountId", "destinationAccountId", "amount", "currency", "type")
                .targetType(TransactionCsvDto.class)
                .build();
    }

    @Bean
    public ItemWriter<ProcessedTransaction> transactionItemWriter(
            TransactionRepository transactionRepository,
            OutboxEventRepository outboxRepository) {
        return chunk -> {
            for (ProcessedTransaction item : chunk.getItems()) {
                transactionRepository.save(item.getTransaction());
                outboxRepository.save(item.getOutboxEvent());
            }
        };
    }

    @Bean
    public Step csvImportStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              FlatFileItemReader<TransactionCsvDto> csvReader,
                              TransactionItemProcessor processor,
                              ItemWriter<ProcessedTransaction> writer,
                              CsvSkipListener skipListener) {
        return new StepBuilder("csvImportStep", jobRepository)
                .<TransactionCsvDto, ProcessedTransaction>chunk(100, transactionManager)
                .reader(csvReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant() // <-- Enables fault tolerance
                .skip(Exception.class) // <-- Skips ANY parsing or writing exception
                .skipLimit(100000) // <-- Max allowed skipped rows
                .listener((SkipListener<TransactionCsvDto, ProcessedTransaction>) skipListener)
                .listener((StepExecutionListener) skipListener)
                .build();
    }

    @Bean
    public Job csvImportJob(JobRepository jobRepository, Step csvImportStep) {
        return new JobBuilder("csvImportJob", jobRepository)
                .start(csvImportStep)
                .build();
    }
}