package com.sentinel.aml.ingestion_service.config;

import com.sentinel.aml.ingestion_service.batch.BatchCompletionListener;
import com.sentinel.aml.ingestion_service.batch.CsvSkipListener;
import com.sentinel.aml.ingestion_service.batch.TransactionItemProcessor;
import com.sentinel.aml.ingestion_service.dto.ProcessedTransaction;
import com.sentinel.aml.ingestion_service.dto.TransactionCsvDto;
import com.sentinel.aml.ingestion_service.exception.InvalidTransactionException;
import com.sentinel.aml.ingestion_service.support.TransactionPersister;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;

@Configuration
public class BatchConfig {

    @Value("${sentinel.batch.chunk-size:500}")
    private int chunkSize;

    /**
     * Columns are taken from the CSV header line, so optional columns
     * (counterpartyName, counterpartyCountry, txnTimestamp) can be present or absent.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<TransactionCsvDto> csvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setStrict(true); // a row with the wrong number of columns is a structural error

        BeanWrapperFieldSetMapper<TransactionCsvDto> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(TransactionCsvDto.class);

        DefaultLineMapper<TransactionCsvDto> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<TransactionCsvDto>()
                .name("transactionCsvReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .skippedLinesCallback(header -> tokenizer.setNames(
                        Arrays.stream(header.replace("﻿", "").split(",")).map(String::trim).toArray(String[]::new)))
                .lineMapper(lineMapper)
                .build();
    }

    @Bean
    public ItemWriter<ProcessedTransaction> transactionItemWriter(TransactionPersister persister) {
        return chunk -> persister.persistAll(chunk.getItems());
    }

    @Bean
    public Step csvImportStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              FlatFileItemReader<TransactionCsvDto> csvReader,
                              TransactionItemProcessor processor,
                              ItemWriter<ProcessedTransaction> writer,
                              CsvSkipListener skipListener) {
        return new StepBuilder("csvImportStep", jobRepository)
                .<TransactionCsvDto, ProcessedTransaction>chunk(chunkSize, transactionManager)
                .reader(csvReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                // Skip only data problems. Infrastructure failures (DB down, etc.) should fail the job
                // instead of silently marking every remaining row as "bad data".
                .skip(InvalidTransactionException.class)
                .skip(FlatFileParseException.class)
                .skip(DataIntegrityViolationException.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job csvImportJob(JobRepository jobRepository, Step csvImportStep, BatchCompletionListener completionListener) {
        return new JobBuilder("csvImportJob", jobRepository)
                .listener(completionListener)
                .start(csvImportStep)
                .build();
    }
}
