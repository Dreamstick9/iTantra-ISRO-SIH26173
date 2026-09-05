# ISRO / Department of Space — all SIH 2026 problem statements

Extracted verbatim from sih.gov.in/sih2026PS HTML snapshot, 2026-09-05.
Context for SIH26173: ISRO's 2026 software asks are uniformly edge/on-device AI under hard constraints.

---

## SIH26166 — Multi-modal, Sun angle and scale invariant image correspondence using Chandrayaan-2 optical images (OHRC, TMC and IIRS)

- **Category:** Software
- **Theme:** Space Technology
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background Image Registration is the process of aligning two or more images of the same scene taken at different times, from different viewpoints, or by different sensors into a common coordinate system.
It has two main components:
• Source Image (Moving): The image that is to be geometrically transformed to align with the reference image.
• Reference Image (Fixed): The target image about which source image is to be geometrically transformed.
Description The process of lunar images registration involves finding match points between source and reference image and then aligning the source image with the reference image. The key challenges involved in this process are as follows:
• Illumination variation: Illumination variation refers to changes in sun azimuth and elevation effect on the surface lighting conditions that affect the appearance of the lunar surface features which is hard to correlate.
• Viewpoint variation: It refers to geometric distortions caused by different camera positions/orientations capturing the same scene. Objects appear shifted, scaled, rotated, or perspective-distorted depending on observing angle.
• Scale Variation: Lunar imaging missions operate at vastly different altitudes and at different spatial resolutions. This creates scale ratios.
Expected Solution Generic software solution for finding correspondence between Chandrayaan-2 acquired optical images and Lunar reference images with a sub-pixel accuracy of source image maintaining uniform distribution across the images.
• Software and registered product with corresponding match points.
• Evaluation metric (eg. RMSE, inlier match count, inlier ratio, etc.)

---

## SIH26167 — SatQuery AI - An Interactive Vision-Language Assistant for Multimodal Remote Sensing Image Analysis through Text Queries

- **Category:** Software
- **Theme:** Space Technology
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background Remote-sensing imagery is widely used for agricultural monitoring, disaster management, urban planning, forest monitoring, water-resource assessment, infrastructure mapping, and environmental analysis. However, most existing remote-sensing AI solutions are developed as isolated applications for a single predefined task, such as land-cover classification, object detection, visual question answering, or change detection. These systems often require users to understand satellite-data characteristics, GIS workflows, model selection, and task-specific parameters. Consequently, non-expert users may find it difficult to obtain meaningful information from satellite imagery through simple natural-language queries.
Many operational remote-sensing questions cannot always be answered reliably using a single optical image. Relevant information may be distributed across paired or multiple observations acquired at different times or by different sensors. Optical and multispectral imagery provides spectral and contextual information, whereas synthetic aperture radar (SAR) provides complementary structural information and supports day-and-night acquisition through cloud cover. Multitemporal image pairs are required to identify and interpret changes over time, while co-registered opticalâ€“SAR pairs can provide more complete and reliable information than either modality alone.
A general-purpose large language model (LLM) or vision-language model (VLM) cannot be expected to perform these specialised tasks reliably without adaptation to remote-sensing imagery, sensor characteristics, and domain-specific terminology. The proposed solution must therefore include remote-sensing fine-tuning or domain adaptation and may employ multiple specialised models for different tasks. BigEarthNet.txt will serve as the primary dataset for adapting imageâ€“text representations to multisensor remote-sensing data. VRSBench and RSVQA will be used to evaluate single-image captioning, grounding, and visual question answering, while CDVQA will be used to evaluate multitemporal change-based visual question answering.
The novelty of SatQuery AI lies in its agentic, query-driven framework. Instead of applying a single generic VLM, the system selects and executes suitable remote-sensing specialist models, validates inputs, combines their outputs, and returns an evidence-grounded response.
Description The objective is to develop SatQuery AI, a software-based agentic vision-language assistant for analysing single and paired remote-sensing images through natural-language queries. Single-image understanding is a mandatory baseline, while the principal focus is joint reasoning over paired cross-modal and multitemporal imagery.
Defined Input Scope
• Single image: One optical/multispectral or SAR image for captioning, visual question answering, and text-guided region grounding.
• Cross-modal pair: Co-registered optical/multispectral and SAR images of the same geographic area for joint information extraction and cross-modal analysis.
• Bi-temporal pair: Two spatially corresponding images of the same geographic area acquired at different times for change detection, change description, and change-based visual question answering.
• Supported formats: GeoTIFF or TIFF for geospatial imagery. PNG and JPEG inputs may be accepted only for the prescribed public benchmark datasets.
Mandatory Functional Scope
• Remote-sensing adaptation: At least one visual or vision-language component must be fine-tuned or otherwise adapted using BigEarthNet.txt or the any open source training data.
• Single-image baseline: Visual question answering shall be mandatory. Each solution must additionally implement either captioning/scene description or text-guided region grounding.
• Multi-image change analysis: Change description or change-based visual question answering from a bi-temporal image pair shall be mandatory. A spatial change map may also be generated where reference masks are available.
• Cross-modal pair analysis: The system must extract complementary information from a co-registered optical/multispectral and SAR image pair.
• Agentic orchestration: The system must automatically select, sequence, and execute the appropriate specialist models or tools according to the query and input configuration.
Representative Queries
• 'Describe the land-cover and major objects visible in this image.'
• 'Highlight the water body referred to in the query.'
• 'What changed between these two dates, and where did the change occur?'
• 'Use the optical and SAR images together to identify built-up and water-covered regions.'
• 'Has the built-up area increased, decreased, or remained unchanged?'
Agentic Model and Tool Orchestration The system may use multiple specialised components, such as a remote-sensing VQA or captioning model, a grounding model, a change-understanding or change-VQA model, and an opticalâ€“SAR fusion or information-extraction model.
• interpret the query and classify the requested task;
• check the number, modality, format, metadata, and compatibility of the input images;
• select one or more models or tools from a predefined registry;
• configure only permitted task parameters and execute the selected workflow;
• combine textual and spatial outputs, estimate confidence, and return visual evidence; and
• provide an auditable execution summary containing the selected task, model/tool names, and key parameters.
The controller may perform internal task planning; however, only the observable execution trace, including the selected task, models or tools, permitted parameters, and outputs will be evaluated. Internal reasoning text is neither required nor evaluated.
Expected Solution The expected solution is an interactive GUI or web application with an agentic remote-sensing AI backend. It should accept supported image inputs and natural-language queries, select the appropriate specialist workflow, and return evidence-grounded textual and visual results.
The solution should include:
• Input upload and compatibility checking.
• A remote-sensing-adapted vision-language component.
• Specialist tools for VQA, captioning or grounding, change understanding, and opticalâ€“SAR analysis.
• An agentic controller for task routing, tool execution, and output integration.
• Visual evidence, confidence information, execution summaries, and downloadable reports.
Each solution must demonstrate single-image VQA, one additional single-image task, multitemporal change understanding, opticalâ€“SAR paired-image analysis, and agentic model/tool orchestration. A generic LLM or VLM without remote-sensing adaptation will not satisfy the requirements.
Deliverables An interactive GUI or web application with an agentic remote-sensing AI backend, Codes and models including test and demonstration.
Implementation Scope The system shall support single optical/multispectral or SAR images, co-registered opticalâ€“SAR pairs, and bi-temporal pairs in GeoTIFF/TIFF or approved benchmark formats. It must perform single-image VQA, one additional single-image task, change analysis, opticalâ€“SAR joint analysis, and agentic model/tool selection through an interactive GUI or web application.
Evaluation/Judging Criteria Final evaluation will use prescribed public benchmark test subsets and an ISRO/SAC evaluation dataset. Scores will be normalised before combining different metrics.
Add 'Evaluation/Judging Criteria' table here Public benchmarks will be evaluated using the prescribed test splits. The ISRO/SAC evaluation set will contain pre-georeferenced and co-registered Cartosat-2S optical and RISAT SAR image pairs, with task-specific reference answers, labels, bounding boxes, or masks, as applicable. Evaluation annotations will not be disclosed to participating teams.

---

## SIH26168 — AI-ML based Intelligent Dead Reckoning system for seamless navigation

- **Category:** Software
- **Theme:** Smart Vehicles
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background Vehicle logistics, ride-hailing services, quick commerce and emergency responders heavily rely on smartphone-based navigation apps (like Google Maps or MapmyIndia) powered by GNSS (GPS/Galileo/NavIC etc). However, when a vehicle enters a long underground tunnel/ underpass, a multi-level parking lot, a dense forested highway, or a deep urban canyon surrounded by skyscrapers, GNSS connectivity drops entirely. GNSS signals are inherently weak and vulnerable to structural blockage (urban canyons, dense foliage, tunnels, deep valleys) and unintentional electromagnetic interferences from variety of sources such as jamming. This causes navigation apps to freeze, jump erratically, or miscalculate upcoming turns, leading to missed exits, delivery delays, and safety hazards.
In these environments, systems must rely on self-contained Inertial Navigation Systems (INS) built from Inertial Measurement Units (IMUs) (accelerometers and gyroscopes) to calculate position via dead reckoning during GNSS outage and switch back to GNSS aided INS after blackout. While INS is immune to external jamming, low-cost tactical or MEMS-grade IMUs suffer from inherent sensor biases, deterministic errors, and thermo-mechanical noise. While modern high-end cars possess factory-fitted, wheel-connected Inertial Navigation Systems (INS), the vast majority of vehicles on Indian roadsâ€”including commercial trucks, older cars, and millions of two-wheelers (motorcycles/scooters)â€”rely solely on the driverâ€™s smartphone mounted on the dashboard or placed in mobile holder.
Using a smartphone's internal MEMS IMU (accelerometer and gyroscope) to track vehicle position via dead reckoning during a GNSS blackout is highly challenging. The smartphone is subjected to severe chassis vibrations, engine harmonics, sudden braking, and road potholes. Without an external speedometer feed from the vehicle's OBD-II port, calculating distance and velocity exclusively from consumer-grade smartphone sensors results in exponential error accumulation, causing the estimated location to drift away within seconds. To overcome these challenges, there is a need for AI-ML enhanced dead reckoning and sensor fusion(GNSS+INS) techniques that integrate AI and machine learning models with real-time correction strategies.
Description The goal is to develop a lightweight, edge-deployable software engine and mobile application that transforms a standalone smartphone into an Intelligent Dead Reckoning (IDR) system with GNSS Fusion. When a GNSS outage occurs, the application must instantly transition to inertial tracking(INS), maintaining lane-level accuracy without requiring any physical connection to the vehicleâ€™s internal computer and seamlessly switch back to GNSS aided INS solution.
To bypass the need for an external speedometer, the solution must employ AI/ML models trained on vehicle kinematics to accurately predict vehicle speed and acceleration profiles solely from the smartphoneâ€™s noisy accelerometer/gyro inputs. It must dynamically detect and filter out non-navigation motions such as engine idling vibrations, pothole shocks, bumps, and accidental phone misalignments on the mount.
Furthermore, the navigation engine should implement a smart Map-Matching Filter. By overlaying the inertial trajectory onto an offline map database (e.g., Open Street Map), the system should use the road layout as a constraint. For instance, it can apply Non-Holonomic Constraints (NHC), assuming a car cannot slide sideways or fly upwards, to dramatically snap the drifting IMU path back onto the actual road grid.
Also, the GNSS+INS fusion Algorithm should employ AI/ML techniques to develop an AI based fusion model to mitigate drift errors and provide accurate position.
The Final solution and AI/ML models developed should not be constricted to smart phone IMU sensors data alone (Mobile application). These algorithms/models should also work with any other external IMU sensors data (Edge deployable software engine).
Dataset Details: IO-VNBD: Inertial and Odometry benchmark dataset for ground vehicle positioning.
This dataset should be used to train & test the models and submit for screening of proposals. Teams are required to include the preliminary AI models and the results of the position plot inferenced from the subset of IO-VNBD dataset as part of their proposals submitted for evaluation. During the screening process more datasets will be provided for further evaluation of the AI models.
The On-Device Workflow Dead reckoning and GNSS fusion algorithms are hybrid. Complex training happens in the cloud/desktop apriori, while inference happens on the smartphone.
1. Model Training (Cloud/Desktop): Teams should train AI-ML models using IMU sensors datasets collected using a smartphone mounted on their vehicles or datasets available in open-source domain (for eg. â€˜IO-VNBD datasetâ€™) and bring trained models with them for SIH finale. Teams can bring the downloaded map database (e.g., Open Street Map).
2. On-Device Execution (Smartphone): During SIH Finale, the trained, lightweight model will need to be exported to the smartphone. It should receive live inputs from the phone's built-in Inertial Measurement Unit (IMU)â€”the accelerometer, gyroscope, and magnetometer/compass and GNSS data if available. The AI-ML based algorithms should remove IMU sensors noise & bias, predict corrections, perform map matching and determine continuous position using AI-ML based algorithms for both dead reckoning and GNSS+INS Fusion. For complete details regarding final solution, refer to the expected solution section.
Expected Solution The final deliverable must be a working mobile application and an Edge deployable software engine exhibiting the following technical capabilities:
• In-Vehicle Alignment & Calibration Engine: An algorithmic module that automatically determines the phoneâ€™s pitch, roll, and yaw relative to the vehicle's driving direction, whether the phone is strictly dashboard-mounted or placed in mobile holder.
• AI Speed & Vibration Filter: A deep-learning or statistical signal-processing model running locally on the phone that filters out high-frequency road noise/potholes and directly estimates vehicle forward velocity from IMU signals.
• Advanced Map-Matching & Kinematic Constraints: A framework (e.g., AI-ML framework or Unscented Kalman Filter + Hidden Markov Map Matching) that binds the calculated position to known road networks and geometric paths during a dropout.
• GNSS+INS Fusion Engine: An innovative AI based Sensor Fusion Algorithm that combines GNSS & IMU measurements and provides significant improvement in overall output by eliminating drift errors and providing accurate position and velocity.
• Seamless GNSS Deficit Handler: An instant seamless transition mechanism between GNSS aided INS and Dead reckoning modes within milliseconds of GNSS signal blackout and vice-versa.
• Real-time Navigation Interface: A functional mobile application with UI displaying a smooth, uninterrupted vehicle icon showing seamless navigation.
Performance Benchmark:
Dead Reckoning: The solution must restrict positional drift to less than 10% of the total distance travelled using smartphone IMUs sensors during GNSS signals blackout (for e.g., in case of smartphones IMU, a drift of less than 5 meters is desired over 50m GNSS denied environment in <1 minutes OR less than 100m of drift over a 1km GNSS denied environment at a speed of 60kmph in tunnels/underground metro OR similar simulated environments where GNSS signals are unavailable).
GNSS+INS Fusion: Position update rate of 10Hz with processing on smartphones (Mobile application) and higher update rates on Edge deployable software engine using FOG based IMU sensors data (around 200Hz).

---

## SIH26169 — Development of an AI-Based Virtual Camera Tracking System for Coarse Alignment of Mobile Free Space Optical Communication (FSOC) Terminals

- **Category:** Software
- **Theme:** Smart Automation
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background Free Space Optical Communication (FSOC) offers unprecedented advantages for next-generation mobile networks, including gigabit-to-terabit data rates, license-free spectrum operation, high immunity to electromagnetic interference, etc. However, deploying FSOC links between mobile platforms (satellites, UAVs presents a severe challenge of pointing, acquisition and tracking (PAT) of highly narrow laser beams. PAT typically happens in two stages: coarse alignment and fine alignment. Coarse alignment is one of the key challenges of PAT, where the transmitting terminal must first locate and maintain the remote terminal within its camera Field-of-View (FOV).
Developing and testing such algorithms on real hardware requires expensive cameras, pan-tilt mechanisms, and optical components & equipment. A software based virtual camera tracking provides an inexpensive and accessible platform for algorithm development and learning.
Description Unlike conventional radio-frequency systems, FSOC relies on a highly directional optical beam. Even a small angular error can prevent successful communication. Before fine pointing mechanism can take over, a coarse alignment stage must:
• Observe the surrounding environment,
• Acquire and detect the remote terminal or beacon,
• Estimate the position, and
• Continuously adjust the pointing direction to maintain visibility.
The participants shall develop this coarse alignment process in software, allowing to develop and validate tracking algorithms without specialized hardware and setup. The following section provides reference parameters and performance criteria to be considered for the software development.
Parameters and Specifications Functional Objective: Develop a software system that autonomously detects, identifies, and continuously tracks a designated moving target within a virtual scene by controlling a virtual camera viewport.
Add 'Parameters and Specifications' table here Expected Solution Participants shall develop an AI-assisted camera tracking system capable of automatically detecting and continuously tracking a moving optical beacon in a simulated video stream while controlling a virtual pan-tilt camera.
The developed software shall be able to:
• Generate a configurable virtual environment,
• Generate one or more moving targets,
• Implement a movable virtual camera,
• Detect the target beacon automatically,
• Track the beacon continuously using computer vision,
• Control and reposition the virtual camera,
• Generate and introduce disturbances due to atmospheric turbulence, platform vibrations, camera motion, noise, etc., in the virtual camera feed,
• Display tracking performance and statistics in real-time Deliverables Each participating team shall submit the following mandatory deliverables:
Software Application A standalone executable application implementing the complete virtual camera tracking system. The application shall provide all the mandatory functions and features as described above.
Source Code Complete source code with proper documentation. The code shall be modular and adequately commented.
Technical Report The technical report (about 10-15 pages) containing problem understanding, system architecture, description of software modules, tracking methods, AI methods (if used), test methodology, performance analysis and future improvements shall be submitted.
User Manual The user manual with the description of installation of software, application operation, parameter configuration, GUI description, etc. shall be submitted. A 3â€“5 minutes video may also be provided as an optional deliverable for demonstration of the application.
Performance Log The software should be capable of automatically generating a performance report containing simulation duration, FPS, acquisition time, average and maximum tracking error, lock retention rate, processing time, etc.
Evaluation Method and Criteria The solutions developed by participating teams will be evaluated using multi-layered evaluation method. The following table describes stages of evaluation, their weightage and methods.
Add 'Evaluation Method and Criteria' table here

---

## SIH26170 — AI-Driven Anomaly Detection in Component Burn-In & Screening

- **Category:** Software
- **Theme:** Smart Automation
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background In high-reliability sectors (like space) electronic components undergo rigorous environmental stress screening (ESS), including Burn-In testing (operating components at elevated temperatures, e.g., 125Â°C for extended periods).
Traditional screening relies on static parametric pass/fail limits. However, 'latent defects'â€”components that pass the absolute limits but exhibit subtle, anomalous drift over timeâ€”often escape into final payloads, leading to catastrophic field failures.
Description Development of a predictive machine learning model that analyzes time-series parametric data (e.g., standby current Iddq, leakage currents, or propagation delays measured at intervals like 0h, 24h, 96h, and 168h to detect anomalous components.
Expected Solution Module A: The outlier detection system Static limits catch obvious failures. Participants need to develop a 'Dynamic' outlier detection system. If a lot has an average leakage current of 10ÂµA, a part showing 45 ÂµA is a massive anomaly, even if the absolute datasheet maximum limit is 50 ÂµA.
Module B: Time-Series Drift Predictor Build a predictive regression model that takes Value_0h and Value_24h as inputs and forecasts Value_168h. If the predicted 168h drift rate exceeds a calculated safety slope, the system flags the component for early rejection.
Evaluation Metrics:
• Anomaly Detection Score: a False Negative (missing a defective part) is catastrophic, penalizing teams that let bad parts escape.
• Drift Prediction Accuracy : The mean absolute error between the predicted Value_168h and the actual hidden ground-truth values.
• Explainability : Can the model justify its classification to a QA inspector, or is it a complete black box?

---

## SIH26171 — On-device Visual Perception for Light-weight Browser Agents

- **Category:** Software
- **Theme:** Smart Automation
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background AI agents are becoming omnipresent in the current era and can play an important role in our digital interactions. If an agentic AI pipeline has access to our visual context, screen states, they can assist users in complex workflows and automate many tasks. Most of the agentic AI pipelines are deployed on server side which limits the type to data that a user can share with it. It would open a new dimension of possibilities, if a local agent is deployed on user machine particularly browser which can eliminate the need to share the sensitive data with the server. Local system generally has fewer resources than server and is unable to host a full-fledged pipeline therefore only the non-sensitive data such as structure of the screen, application fields etc can be sent to server for processing.
Modern browser APIs (such as WebGPU and WebAssembly) and local inference libraries (like ONNX Runtime Web and Transformers.js) have unlocked the ability to run lightweight machine learning models directly on the client. The aim is to bridge these two environments: leveraging the reasoning power of cloud or server based AI while strictly enforcing data privacy at the client side.
Description Participants are required to build a privacy-preserving vision agent which runs on browser. This involves implementing a client-side architecture where a local Vision Transformer (ViT) or equivalent computer vision model 'reads' the user's screen and takes decision based on that. If it requires the visual context to be sent to server, it shall sanitize the sensitive/PII data using DOM tags or any other method, before any network request is made. It should dynamically detect and redact sensitive elements. For example, blurring faces, blacking out passwords, and masking PII etc. Only this anonymized, unidentifiable data should be transmitted to the central server which should be aware for this redaction scheme and can process data accordingly. The server will then process the sanitized context and return actionable commands for the browser agent to execute. Participants must balance the trade-offs between inference latency and the accuracy.
Expected Solution A successful submission should include a working prototype consisting of client side extension and server that demonstrates the following:
Client-side (extension/JS) running in popular browsers (chrome, Firefox) components:
• Local Vision Processing: Implementation of a client-side vision model running in the browser (e.g., via WebGPU) that evaluates the current screen state.
• Privacy Preserving Filter: A mechanism for sanitizing sensitive or personal visual data. This can be achieved through local bounding-box redaction, semantic obfuscation, masking etc. This should be clearly demonstrated.
Server-side implementation components:
• Server Side Integration: The transmission of the anonymized visual context to a centralized LLM/VLM, which successfully interprets the sanitized data and returns the response which may be processed data to be again ingested by local client or an UI action (e.g., 'click the submit button,' 'scroll down') that the local client executes.
• Participants are free to use any offline deployable (open-source/open-weights) model on server side. During SIH they can use cloud hosted version of these. An end-to-end task assisting the user should be demonstrated.
Evaluation will be done on the following metrics:
1-Accuracy of visual context from screen â€“ 25% 2-Recall and precision for detection of sensitive/PII data â€“ 20% 3-Precision of redaction â€“ 20% 4-Client side resource utilization â€“ 20% 5-Overall end-to-end latency of the provided task -15%

---

## SIH26172 — Low Latency and Efficient Voice Activator for Edge Devices

- **Category:** Hardware
- **Theme:** Smart Automation
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background As voice-controlled IoT proliferate, processing everything in the cloud is too costly, privacy-invasive, and slow. The future belongs to hybrid architectures where the edge handles the initial 'wake-up' and the cloud handles the heavy lifting.
Description Build an ultra-lightweight, highly accurate keyword spotting (KWS) model that runs locally on a low-power device. Upon detecting the keyword, the system must instantly and efficiently stream the subsequent audio to a remote Automated Speech Recognition (ASR) server with minimal data overhead and latency.
Key Metrics for Evaluation
• Efficiency: Model size (RAM/Flash footprint) and CPU usage during idle listening.
• Accuracy: High true-positive rate for the keyword with near-zero false activations.
• Latency: The time delta between the keyword ending and the cloud ASR receiving the audio stream.
Software & Framework Restrictions
• Open-Source Only: The use of proprietary, closed-source, or commercial voice-activation SDKs is strictly prohibited.
• Allowed Frameworks: Teams must build their keyword spotting (KWS) pipelines using open-source machine learning and TinyML frameworks. Recommended tools include TensorFlow Lite for Microcontrollers, PyTorch Mobile or similar.
• No Pre-Trained Global Keywords: Teams cannot use models pre-trained on generic smart-assistant keywords like 'Hey Google' or 'Alexa'. They need to train on a custom key word.
Expected Solution Teams are expected to deliver a robust, deployable system architecture. A successful submission must strictly satisfy the following technical boundaries:
• Hardware & Runtime Environment: The edge software application must run smoothly within an environment restricted to less than 256KB of RAM and consume under 10% CPU utilization while idling in continuous listening mode. Heavy or uncompressed pre-trained transformers are disqualified. Solutions will be formally evaluated on physical low-power microcontrollers (e.g., Raspberry Pi or ESP32).
• Model should work for the given custom key word.

---

## SIH26173 — iTantra -Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links

- **Category:** Software
- **Theme:** Smart Automation
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background As vocal audio information is very data intensive making it difficult to transmit through low data rate links. In alert and distress based scenarios Transmitting Audio information is critical instead of written message as it will be more inclusive and will cater to everyone even if they are literate or not.
Description Build an Android App with lightweight, highly accurate STT and TTS models for 10 Indian Languages (Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English) that runs locally on a low-power device. The systemâ€™s STT module when activated after detecting pauses and stoppages should form the sentences detected and must instantly and efficiently stream the data through wifi/Bluetooth connected embedded device or another phone with same application with minimal latency. The systems TTS module when activated after receiving the Text data should convert it into intelligible speech which will be played as a voice note and alert type messages will be announced at highest volume non-interruptible. To verify the complete loop two phones with same app one in TTS mode and another in STT mode can be connected via wifi or Bluetooth and it should work like a walkie talkie using push to talk feature, if turned off it should work like a phone.
Key Metrics for Evaluation
• Efficiency: Model size, App size (RAM/Flash footprint) and CPU usage during idle listening. (20%)
• Accuracy: Low Word Error Rate for STT and High human legibility and flow for TTS. (40%)
• Latency: The Time delay between the Words said and STT completion, Time delay between the text received and audio processed and played for TTS along with RTF (Real Time Factor). The time delta between the sentence said and the same sentence started as audio in another phone. (20%)
Software & Framework Restrictions
• Open-Source Only: The use of proprietary, closed-source, or commercial voice-activation SDKs is strictly prohibited.
• Allowed Frameworks: Teams must build their pipelines using open-source machine learning and TinyML frameworks. Recommended tools include TensorFlow Lite for Microcontrollers, PyTorch Mobile or similar.
• Fully Offline Working: Model or pipeline should work fully offline only and no internet hosted API based solutions are expected and encouraged for the STT or TTS.
Expected Solution Teams are expected to deliver a robust, deployable system architecture. A successful submission must strictly satisfy the following technical boundaries:
• Hardware & Runtime Environment: The Android application must run smoothly on Low and Mid rage mobile phones.

---

## SIH26174 — AI Human Activity Recognition for On-board BAS Experiments

- **Category:** Software
- **Theme:** Space Technology
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background As humanity aims for space missions such as BAS and lunar missions, real-time ground support becomes impossible due to communication delays. An AI-based HAR system acts as an on-board assistant that supports the execution of scientific experiments, ensuring the success of science beyond Earth's orbit.
In the space environment, AI-based HAR system may act as mission-critical support for astronauts. By tracking astronaut movements and activities in real time, HAR ensures scientific experiments and related protocols are executed flawlessly without requiring constant, high-bandwidth communication with mission control.
Description Challenge is to design and train an AI model that recognizes and validates the sequence of a pre-defined experiment using human activity recognition techniques.
Standalone operation: Space stations operate on restricted data bandwidth to Earth. Rather than streaming raw video to ground control, data is processed locally at the 'edge.' Inputs are given from fixed-payload cameras.
Dataset generation to train model for object detection, pose estimation and hand-object interaction based on the steps of the experiment.
Optional: Another challenge is that Standard 2D or ground-based 3D posture models fail because astronauts do not have a fixed 'up' or 'down' orientation. The AI model should use orientation-agnostic 3D Human Mesh Recovery (HMR) to track the astronautâ€™s body relative to the payload rack, not the floor.
Expected Solution
• The software should continuously process local video feeds to track the sequence of experiment.
• At the start or after each step, the model should suggest the next step to be performed.
• It should alert when a step is skipped or an out of sequence step is added. It should be a voice based alert.
• Using the live video, it should generate a timestamped and structured lightweight text file of the conducted steps with outcomes/ status.
• Stream the video of the experiment to specific IP and also store the video locally.
• A graphical user interface for monitoring the above activities.
• Deliverable: A trained AI model that runs on offline standalone system

---

## SIH26175 — DepthWizard - Single-View Height Estimation and 3D Flythrough

- **Category:** Software
- **Theme:** Disaster Management
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background Accurate Digital Elevation Models (DEMs) and Digital Surface Models (DSMs) are fundamental to urban planning, disaster management, and military reconnaissance. Traditionally, elevation data is acquired through stereo-imaging pairs, LiDAR, or Interferometric Synthetic Aperture Radar (InSAR). These approaches can be cost-prohibitive, dependent on specific sensor availability, and computationally intensive. Single-view height estimation offers an agile alternative, but foundational monocular depth models are trained largely on natural egocentric imagery and predict relative depth. When applied to remote sensing, they face domain gaps, structural variations, and a lack of absolute-scale mapping. Converting relative depth into metric elevation remains a critical challenge, alongside the operational need to transform static elevation profiles into interactive 3D assets that can be navigated in real time.
Description Develop an end-to-end software pipeline that transforms single-view optical RGB remote-sensing images into high-precision elevation maps. The framework must support both non-georeferenced and georeferenced imagery.
• Non-Georeferenced RGB Imagery (for example, PNG or JPG): Produce a Relative Digital Surface Model (rDSM) for images without spatial metadata.
• Georeferenced RGB Imagery (for example, GeoTIFF): Produce an Absolute Digital Surface Model (DSM) with metric height values for images containing coordinate-system metadata.
The solution should use a pre-trained monocular depth-estimation backbone to generate initial relative-depth maps. For georeferenced imagery, a lower-resolution DEM source such as SRTM or a limited set of Ground Control Points may be used to map scale-agnostic depth features to absolute metric elevations. For non-georeferenced imagery, relative height may be used directly in the visualization stage.
After computing the elevation map, the system should project the original optical image onto a generated 3D terrain mesh and integrate the result with a rendering engine such as Unity, Three.js, or Babylon.js. The interface should support seamless first-person navigation and analysis of structural heights and slopes from arbitrary aerial perspectives.
Key Milestones
• Elevation Extraction: Use a robust pre-trained monocular depth model to extract geometric and structural representations from single-view optical imagery.
• Scale Calibration: Develop a module that converts relative depth to absolute height using scene-level statistics, low-resolution DEMs, semantic priors, or minimal Ground Control Points for georeferenced inputs.
• Visualization Layer: Build an immersive, preferably interactive, rendering pipeline that converts the optical texture and derived depth map into a navigable 3D environment deployable as a standalone application.
Evaluation Criteria:
• DSM Estimation - Accuracy and Validation (50%): Evaluate RMSE, MAE, and correlation against LiDAR or reference data, including performance stability across urban, sparse, hilly, and forested landscapes.
• Visualization - Rendering Quality and User Experience (50%): Assess projection accuracy, visual fidelity, navigability of the 3D flythrough, interface intuitiveness, software stability, and successful standalone deployment.
Expected Solution Deliver a fully integrated software suite with complete source code and technical documentation. The solution must be deployable as a unified module containing the following components:
• Elevation Estimation Module: Accept single-view optical satellite imagery in PNG, JPG, or TIFF format and output a high-fidelity DSM in a standard geospatial format.
• Interactive Visualization Platform: Provide a user-friendly 3D flythrough experience that lets users upload imagery, visualize reconstructed terrain, and validate estimated height values against reference datasets.

---

## SIH26176 — ORCA Marine EcOsystem Reasoning with Collaborative Agents

- **Category:** Software
- **Theme:** Disaster Management
- **Organization:** Indian Space Research Organisation(ISRO)
- **Department:** Department of Space / Indian Space Research Organisation

### Description

Background The marine ecosystem plays a vital role in supporting livelihoods, food security, biodiversity, maritime transportation, coastal resilience, and the blue economy. Every day, vast volumes of satellite Earth Observation and oceanographic data, including Sea Surface Temperature (SST), chlorophyll concentration, and weather forecasts, are generated by ISRO and other global agencies.
Marine stakeholders such as fishermen, researchers, coastal authorities, disaster management agencies, and maritime operators rely on timely access to oceanographic and meteorological information for operational planning and decision-making. As the volume, diversity, and complexity of marine data continue to increase, there is a growing need for an intelligent conversational platform that enables users to interact naturally with marine information, ask questions, explore scenarios, and receive synthesized, evidence-based recommendations tailored to their context.
Advances in Agentic AI, conversational intelligence, and geospatial technologies present an opportunity to fundamentally transform how marine information is accessed and utilized. By integrating satellite Earth Observation data with autonomous AI agents, intelligent conversational decision-support systems should be able to answer questions such as 'Where is the nearest Potential Fishing Zone today?', 'Is it safe to venture into the sea tomorrow morning?', or 'What are the tide, weather, and sea conditions near my fishing location?' and provide explainable, context-aware recommendations.
Description Develop an Agentic AI-powered conversational platform that enables users to access, analyze, and reason over marine information using natural language.
The platform should autonomously interpret user intent, decompose complex requests into executable tasks, coordinate multiple specialized AI agents, retrieve relevant marine and geospatial datasets, perform spatial-temporal reasoning, and synthesize actionable recommendations through a conversational interface.
The solution should be capable of integrating information from multiple sources, including satellite Earth Observation products, GIS layers, weather services, oceanographic observations, and marine advisories available in the public domain.
Typical user queries include:
• Where is the nearest Potential Fishing Zone (PFZ) today?
• Is it safe to venture into the sea tomorrow morning?
• What are the tide, weather, and sea conditions near my fishing location?
• Are there any lightning or cyclone alerts in my area?
• Which regions show high chlorophyll concentration and favourable sea surface temperature?
• What is the safest route for a fishing vessel considering weather and sea-state conditions?
• Why has fish productivity declined in a particular coastal region?
• Which fishing zones should be avoided due to hazardous marine conditions or geofencing restrictions?
The platform should not merely retrieve information from individual datasets but intelligently correlate observations from multiple sources, explain the reasoning behind its recommendations, and present insights through conversational responses, maps, alerts, and interactive geospatial visualizations.
Expected Solution Participants are expected to develop an Agentic AI-powered Marine Intelligence Platform that leverages collaborative AI agents, geospatial technologies, and satellite Earth Observation data to provide intelligent conversational decision support.
The solution should demonstrate the core principles of Agentic AI, including autonomous planning, reasoning, tool selection, task execution, collaboration among specialized agents, and explainable decision-making.
The platform should be capable of:
• Understanding user intent expressed in natural language.
• Automatically identifying the language of the user's query and responding in the same language, with emphasis on supporting Indian regional languages.
• Supporting contextual, multi-turn conversations that enable users to refine queries and explore related scenarios.
• Autonomously discovering, retrieving, and integrating relevant satellite, marine, meteorological, and geospatial datasets.
• Performing spatial, temporal, and contextual reasoning by correlating observations from multiple heterogeneous data sources.
• Generating explainable, evidence-based recommendations supported by maps, charts, geospatial visualizations, and marine advisories.
• Enhancing fishermen safety through proactive alerts for adverse weather, high waves, lightning, cyclones, and other hazardous marine conditions.
• Providing geofencing-based notifications when approaching international maritime boundaries, restricted waters, marine protected areas, ecologically sensitive zones, or other predefined operational boundaries.
• Assisting with route optimization, safe navigation, and operational planning based on prevailing and forecast marine conditions.
• Delivering reliable recommendations together with the supporting evidence and reasoning used to derive each response.
Participants are encouraged to design a modular multi-agent architecture comprising specialized AI agents for planning, marine data discovery, weather intelligence, ocean analytics, geospatial reasoning, risk assessment, visualization, reporting, and user interaction. The architecture should demonstrate autonomous collaboration among agents to solve complex marine intelligence problems while providing an intuitive conversational experience.

---

