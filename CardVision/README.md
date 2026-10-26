# Card Vision
Course project for Intelligent Systems, M.Sc Computer Engineering, Università di Pisa. A.Y 2025/2026.
##### Table of Contents  
- [Project description](#project-description)  
- [Project structure](#project-structure)
- [Run intructions](#instructions)

## Project structure
The project structure is the following:
```
.
├───datasets/
│   ├───original/
│   └───pcd_merge_kfcv/
|
├───notebooks/
│   ├─── exploration.ipynb
│   ├─── preparation.ipynb
│   ├─── train_kfold.ipynb
│   ├─── comparison.ipynb
│   └─── scr_metric.ipynb
|
├───models/
│   ├───yolov11/
│   │   ├───nano/
│   │   ├───small/
│   │   ├───medium/
│   │   └───large/
│   └───selected/
|
├───app/
|
└─── README.md (this file)
```
Note that many files and folders are not shown to increase readability.


<details>

<summary>Complete structure:</summary>

```
.
├───datasets/
│   ├───original/
│   |   ├─── archive.zip
│   |   └─── Playing Cards.v3-original_raw-images.yolov11.zip
│   └───pcd_merge_kfcv/
│       ├───images/
│       ├───labels/
│       ├───plots/
│       └───yamls/
|
├───notebooks/
│   ├─── exploration.ipynb
│   ├─── preparation.ipynb
│   ├─── train_kfold.ipynb
│   ├─── comparison.ipynb
│   ├─── scr_metric.ipynb
|   ├───comparison_plots/
│   │   └─── *.png
|   ├───datasets_samples/
│   │   └─── *.png
|   └───datasets_info/
│       └─── *.png
|
├───models/
│   ├───yolov11/
│   │   ├───nano/
│   │   ├───small/
│   │   ├───medium/
│   │   └───large/
│   └───selected/
│       ├─── complete_detections.json
│       └─── best.pt
|
├───app/
│   ├─── main.py
│   ├─── main.py
│   ├─── detector.py
│   └─── app.sh
|
└─── README.md (this file)
```

</details>

## Project description
### Datasets (`datasets` folder)
The `datasets` folder contains the original datasets used in this project and the processed dataset used for training and evaluation.

The `original` folder contains the raw downloaded datasets (as .zip files) before any preprocessing step. The `pcd_merge_kfcv` folder contains the final merged dataset obtained after the preparation phase, including the images, annotations, split files, and generated plots.


### Notebooks (`notebooks` folder)
The notebooks are meant to be read (and executed) in a fixed, precise order:
- `exploration.ipynb` The two chosen datasets are explored. Datasets are extracted, some statistics are printed and visualized, some random samples from both datasets are shown.
- `preparation.ipynb` The two datasets are merged into one dataset, and the annotation file structure is prepared for K-Fold Cross validation.
- `train_kfold.ipynb` Notebook used to train all evaluated YOLO variants
- `comparison.ipynb` All trained models are evaluated according to many different metrics, plots are created.
- `scr_metric.ipynb` The Same-Card recognition problem is introduced and addressed, via a statistical analysis of geometric properties of Same-Card bounding box pair

### Models (`models` folder)
The `models` folder contains all trained YOLO models and the final selected model used for the prototype application.

The `yolov11` folder contains the trained models for each evaluated YOLOv11 variant (`nano`, `small`, `medium`, and `large`). Each variant folder is then subdivided into five folders, one for each fold (`fold_0`, `fold_1`, `fold_2`, `fold_3`, `fold_4`).

Within each `models/yolov11/<variant>/fold_<idx>/` folder are located results obtained during training. The most important files are:
- `weights/best.pt`: trained weights of the model.
- `eval/*.png`: various plot generated during the test phase.

---

The `selected` folder contains the model chosen for the final system implementation. In particular:
- `best.pt` contains the trained weights of the selected YOLO model. This is just a copy of the `best.pt` file from the selected model's folder; it has been placed here for an easier export of the model
- `complete_detections.json` contains detection results used for the Same-Card Recognition problem.


### Prototype Application (`app` folder)
The `app` folder contains the source code of the prototype Intelligent System developed in this project.

The main components of the application are:
- `main.py`: application entry point. It manages the interaction with the user, requests the input images containing the player's hand and the optional board cards, validates the detected cards, and coordinates the execution of the detection and poker analysis modules. It also collects the number of opponents and the number of Monte Carlo iterations used for the probability estimation.

- `detector.py`: contains the card detection module based on the selected YOLO model. The `CardDetector` class loads the trained weights and performs inference on input images. Moreover, the module extracts geometric information from detections and performs a post-processing step based on the Same-Card Recognition metric to identify and remove duplicate detections corresponding to the same physical card.


- `poker.py`: implements the poker analysis engine. The module evaluates poker hands, computes the best possible five-card combination, estimates the probability of winning, tying, or losing through a Monte Carlo simulation, and provides a suggested action (`RAISE`, `CALL`, or `FOLD`) based on the estimated winning probability.

- `app.sh`: just a script usable to launch the application without directly executing `python3 main.py`.


The folder also contains several sample images used as input examples for testing the prototype.

The application is implemented as a command-line prototype and does not provide a graphical user interface. It requires an image of the player's hand as input and optionally an image of the table cards. Input images are provided by specifying their filename and must be located inside the application folder.

After processing the images, the application prints the results directly to the terminal. The output includes the estimated probabilities of winning, tying, and losing the hand, together with a suggested action for the player (`RAISE`, `CALL`, or `FOLD`).


## Instructions
The project has been developed in a private environment provided by the University. Given this all directory and file paths in the project are relative to this running environment, whose root directory is `/workspace`.

To run the project in a different environement is sufficient to change the root directory to the absolute path of the chosen root directory where the project will live. The internal structure will be the same as the one shown in [Project structure](#project-structure).

--- 

Due to limitations in the uploading of a large number of files, some components of the project are submitted in a compressed manner. To reproduce the original, uncompressed complete project structure one must extract all provided .zip files in the parent directory to where they are given, without changing the file/directory name. To check that the uncompression has been concluded successfully, just compare the resulting project structure with the *Complete structure* given in [Project structure](#project-structure).

---

To reproduce the presented results the notebooks must be executed in the presented order.