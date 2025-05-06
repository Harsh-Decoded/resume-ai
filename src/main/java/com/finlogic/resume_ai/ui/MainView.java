package com.finlogic.resume_ai.ui;

import com.finlogic.resume_ai.model.EmailResponse;
import com.finlogic.resume_ai.model.Job;
import com.finlogic.resume_ai.model.ResumeAnalysisResult;
import com.finlogic.resume_ai.service.JobService;
import com.finlogic.resume_ai.service.ResumeAnalyzerService;
import com.finlogic.resume_ai.util.CustomMultipartFile;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.Route;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Route("")
public class MainView extends VerticalLayout implements BeforeLeaveObserver {

    private static final Logger LOGGER = Logger.getLogger(MainView.class.getName());

    private final JobService jobService;
    private final ResumeAnalyzerService resumeAnalyzerService;

    private ComboBox<Job> jobComboBox;
    private Upload upload;
    private MultiFileMemoryBuffer buffer;
    private Button analyzeButton;
    private Button sendEmailButton;
    private ProgressBar progressBar;
    private Grid<ResumeAnalysisResult> resultGrid;
    private List<MultipartFile> uploadedFiles = new ArrayList<>();
    private List<ResumeAnalysisResult> analysisResults = new ArrayList<>();
    private boolean isAnalyzing = false;

    @Autowired
    public MainView(JobService jobService, ResumeAnalyzerService resumeAnalyzerService) {
        this.jobService = jobService;
        this.resumeAnalyzerService = resumeAnalyzerService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        setupComponents();
        setupLayout();
        setupListeners();
        setupSessionKeepAlive();
    }

    private void setupComponents() {
        H2 title = new H2("Resume Analyzer");

        // Job selection
        jobComboBox = new ComboBox<>("Select Job Requirement");
        jobComboBox.setItems(jobService.getJobs());
        jobComboBox.setItemLabelGenerator(Job::getTitle);
        jobComboBox.setWidthFull();

        // File upload
        buffer = new MultiFileMemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "application/zip", "application/x-zip-compressed");
        upload.setMaxFiles(100);
        upload.setDropAllowed(true);
        upload.setWidthFull();

        // Buttons and Progress
        analyzeButton = new Button("Analyze");
        analyzeButton.setEnabled(false);
        analyzeButton.setWidthFull();

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setWidthFull();

        // Results grid
        resultGrid = new Grid<>();
        resultGrid.addColumn(ResumeAnalysisResult::getResumeName).setHeader("Resume Name");
        resultGrid.addColumn(ResumeAnalysisResult::getScore).setHeader("Score");
        resultGrid.addColumn(ResumeAnalysisResult::getSelected).setHeader("Selected");
        resultGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        resultGrid.setWidthFull();
        resultGrid.setHeight("300px");
        resultGrid.setVisible(false);

        // Email button
        sendEmailButton = new Button("Send Email to Selected");
        sendEmailButton.setEnabled(false);
        sendEmailButton.setVisible(false);
        sendEmailButton.setWidthFull();
    }

    private void setupLayout() {
        Paragraph instructions = new Paragraph("Select a job requirement, upload PDF resumes or a ZIP file containing PDFs, and click 'Analyze'.");

        add(
                new H2("Resume Analyzer"),
                instructions,
                jobComboBox,
                upload,
                analyzeButton,
                progressBar,
                resultGrid,
                sendEmailButton
        );
    }

    private void setupListeners() {
        jobComboBox.addValueChangeListener(event -> updateButtonState());

        upload.addSucceededListener(event -> {
            String fileName = event.getFileName();
            try {
                InputStream inputStream = buffer.getInputStream(fileName);
                byte[] fileContent = IOUtils.toByteArray(inputStream);

                if (FilenameUtils.getExtension(fileName).equalsIgnoreCase("zip")) {
                    processZipFile(fileName, fileContent);
                } else {
                    CustomMultipartFile file = new CustomMultipartFile(
                            fileName, "application/pdf", fileContent);
                    uploadedFiles.add(file);
                }

                updateButtonState();
                Notification.show("File uploaded: " + fileName, 3000, Notification.Position.BOTTOM_END);
            } catch (IOException e) {
                LOGGER.severe("Failed to process file " + fileName + ": " + e.getMessage());
                Notification notification = Notification.show("Failed to process file: " + e.getMessage());
                notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        analyzeButton.addClickListener(event -> analyzeResumes());

        sendEmailButton.addClickListener(event -> sendEmails());
    }

    private void processZipFile(String fileName, byte[] fileContent) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileContent))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().toLowerCase().endsWith(".pdf")) {
                    byte[] pdfBytes = IOUtils.toByteArray(zis);
                    CustomMultipartFile file = new CustomMultipartFile(
                            zipEntry.getName(), "application/pdf", pdfBytes);
                    uploadedFiles.add(file);
                }
            }
        }
    }

    private void updateButtonState() {
        analyzeButton.setEnabled(jobComboBox.getValue() != null && !uploadedFiles.isEmpty() && !isAnalyzing);
    }

    private void setupSessionKeepAlive() {
        UI.getCurrent().setPollInterval(5000); // Poll every 5 seconds to keep session alive
        LOGGER.info("Session keep-alive polling set to 5 seconds");
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        LOGGER.warning("Navigation detected: Leaving MainView, isAnalyzing=" + isAnalyzing);
        if (isAnalyzing) {
            LOGGER.info("Analysis in progress, consider delaying navigation");
            // Optionally, prompt user or delay navigation
            // event.postpone(); // Uncomment to prevent navigation during analysis
        }
    }

    private void analyzeResumes() {
        if (uploadedFiles.isEmpty() || jobComboBox.getValue() == null) {
            Notification.show("Please select a job and upload files first");
            return;
        }

        isAnalyzing = true;
        analyzeButton.setEnabled(false);
        progressBar.setVisible(true);
        resultGrid.setVisible(false);
        sendEmailButton.setVisible(false);
        LOGGER.info("Starting resume analysis, files: " + uploadedFiles.size());

        MultipartFile[] fileArray = uploadedFiles.toArray(new MultipartFile[0]);
        Job selectedJob = jobComboBox.getValue();
        int jobId = selectedJob.getId();

        CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Calling resumeAnalyzerService.analyzeResumes for job ID: " + jobId);
                List<ResumeAnalysisResult> results = resumeAnalyzerService.analyzeResumes(fileArray, jobId);
                LOGGER.info("Received " + results.size() + " analysis results");
                return results;
            } catch (Exception e) {
                LOGGER.severe("Resume analysis failed: " + e.getMessage());
                throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
            }
        }).whenComplete((results, throwable) -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    LOGGER.info("Updating UI with analysis results, throwable=" + (throwable != null));
                    if (throwable != null) {
                        Notification notification = Notification.show("Analysis failed: " + throwable.getMessage());
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                        LOGGER.severe("UI update error: " + throwable.getMessage());
                    } else {
                        analysisResults = results;
                        LOGGER.info("Setting resultGrid with " + analysisResults.size() + " items");
                        resultGrid.setItems(analysisResults);
                        resultGrid.setVisible(true);
                        sendEmailButton.setVisible(true);
                        sendEmailButton.setEnabled(true);
                        Notification.show("Analysis complete, displaying " + analysisResults.size() + " results", 3000, Notification.Position.BOTTOM_END);
                    }
                } catch (Exception e) {
                    LOGGER.severe("UI update failed: " + e.getMessage());
                    Notification notification = Notification.show("Failed to update UI: " + e.getMessage());
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                } finally {
                    progressBar.setVisible(false);
                    analyzeButton.setEnabled(true);
                    isAnalyzing = false;
                    updateButtonState();
                    LOGGER.info("Analysis completed, UI updated");
                }
            }));
        });
    }

    private void sendEmails() {
        Set<ResumeAnalysisResult> selectedItems = resultGrid.getSelectedItems();
        if (selectedItems.isEmpty()) {
            Notification.show("Please select at least one resume");
            return;
        }

        List<ResumeAnalysisResult> selectedResumes = new ArrayList<>(selectedItems);
        sendEmailButton.setEnabled(false);
        progressBar.setVisible(true);
        LOGGER.info("Starting email sending for " + selectedResumes.size() + " resumes");

        CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Calling resumeAnalyzerService.sendEmails");
                EmailResponse response = resumeAnalyzerService.sendEmails(selectedResumes);
                LOGGER.info("Email sending completed, success: " + response.isSuccess());
                return response;
            } catch (Exception e) {
                LOGGER.severe("Email sending failed: " + e.getMessage());
                throw new RuntimeException("Failed to send emails: " + e.getMessage(), e);
            }
        }).whenComplete((response, throwable) -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    LOGGER.info("Updating UI with email results, throwable=" + (throwable != null));
                    if (throwable != null) {
                        Notification notification = Notification.show("Failed to send emails: " + throwable.getMessage());
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                        LOGGER.severe("Email UI update error: " + throwable.getMessage());
                    } else {
                        if (response.isSuccess()) {
                            Notification.show("Emails sent successfully to " + response.getSentTo().size() + " recipients");
                        } else {
                            Notification notification = Notification.show(
                                    "Some emails failed to send. Successful: " + response.getSentTo().size() +
                                            ", Failed: " + response.getFailedTo().size());
                            notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.severe("Email UI update failed: " + e.getMessage());
                    Notification notification = Notification.show("Failed to update UI: " + e.getMessage());
                    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                } finally {
                    progressBar.setVisible(false);
                    sendEmailButton.setEnabled(true);
                    LOGGER.info("Email sending completed, UI updated");
                }
            }));
        });
    }
}