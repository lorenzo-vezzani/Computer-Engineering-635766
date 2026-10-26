import os
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    confusion_matrix,
    classification_report,
    roc_auc_score,
    roc_curve,
)

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

import emlearn


# =========================================================
# Configuration
# =========================================================

DATASET = "iot-dataset.csv"

FEATURES = [
    "Temperature",
    "Humidity",
    "Light",
    "CO2",
]

TARGET = "Occupancy"

# 70% development / 30% final test
TEST_SIZE = 0.30

# 85% training / 15% validation within development set
VALIDATION_SIZE = 0.15

RANDOM_SEED = 42

THRESHOLD = 0.5


# Reproducibility
np.random.seed(RANDOM_SEED)
tf.random.set_seed(RANDOM_SEED)


# =========================================================
# 1. Load dataset
# =========================================================

df = pd.read_csv(DATASET)

print("Shape:", df.shape)

print("\nColumns:")
print(df.columns.tolist())

print("\nFirst rows:")
print(df.head())


# =========================================================
# 2. Clean column names
# =========================================================

df.columns = df.columns.str.strip()

print("\nCleaned columns:")
print(df.columns.tolist())


# =========================================================
# 3. Convert and sort by date
# =========================================================
#
# The dataset is temporal, therefore the samples are sorted
# chronologically before performing the hold-out split.
# =========================================================

df["date"] = pd.to_datetime(
    df["date"],
    format="%m/%d/%y %H:%M",
    errors="coerce"
)

df = df.sort_values(
    "date"
).reset_index(drop=True)


# =========================================================
# 4. Check data
# =========================================================

print("\nMissing values:")
print(df.isnull().sum())

print("\nOccupancy distribution:")
print(df[TARGET].value_counts())

print("\nOccupancy percentage:")
print(
    df[TARGET].value_counts(normalize=True) * 100
)


# =========================================================
# 5. Numeric conversion
# =========================================================

for col in FEATURES + [TARGET]:
    df[col] = pd.to_numeric(
        df[col],
        errors="coerce"
    )

df = df.dropna(
    subset=FEATURES + [TARGET, "date"]
).reset_index(drop=True)

print("\nShape after cleaning:")
print(df.shape)


# =========================================================
# 6. Features / target
# =========================================================

X = df[FEATURES].astype(np.float32)
y = df[TARGET].astype(np.int32)

print("\nFeatures:")
for feature in FEATURES:
    print("-", feature)

print("\nTarget:", TARGET)

print("\nDataset temporal range:")

print(
    "Start:",
    df["date"].iloc[0]
)

print(
    "End:",
    df["date"].iloc[-1]
)


# =========================================================
# 7. Temporal 70/30 hold-out split
# =========================================================
#
# First 70%:
#   Development data
#
# Last 30%:
#   Completely isolated final test data
#
# No shuffling is performed.
# =========================================================

split_index = int(
    len(X) * (1.0 - TEST_SIZE)
)


X_dev = X.iloc[
    :split_index
].reset_index(drop=True)

y_dev = y.iloc[
    :split_index
].reset_index(drop=True)


X_test = X.iloc[
    split_index:
].reset_index(drop=True)

y_test = y.iloc[
    split_index:
].reset_index(drop=True)


print("\n" + "=" * 60)
print("TEMPORAL 70/30 HOLD-OUT SPLIT")
print("=" * 60)

print(
    "Development:",
    len(X_dev),
    f"({len(X_dev) / len(X) * 100:.1f}%)"
)

print(
    "Final test:",
    len(X_test),
    f"({len(X_test) / len(X) * 100:.1f}%)"
)

print("\nDevelopment period:")

print(
    df["date"].iloc[0],
    "->",
    df["date"].iloc[split_index - 1]
)

print("\nFinal test period:")

print(
    df["date"].iloc[split_index],
    "->",
    df["date"].iloc[-1]
)

print("\nDevelopment class distribution:")

print(
    y_dev.value_counts().sort_index()
)

print("\nTest class distribution:")

print(
    y_test.value_counts().sort_index()
)


# =========================================================
# 8. Internal temporal train/validation split
# =========================================================
#
# The development set is further divided chronologically:
#
#   85% -> training
#   15% -> validation
#
# The validation set is used only for early stopping.
# The final 30% test set remains completely isolated.
# =========================================================

validation_split_index = int(
    len(X_dev) * (1.0 - VALIDATION_SIZE)
)


X_train = X_dev.iloc[
    :validation_split_index
].reset_index(drop=True)

y_train = y_dev.iloc[
    :validation_split_index
].reset_index(drop=True)


X_val = X_dev.iloc[
    validation_split_index:
].reset_index(drop=True)

y_val = y_dev.iloc[
    validation_split_index:
].reset_index(drop=True)


print("\n" + "=" * 60)
print("TEMPORAL TRAIN/VALIDATION SPLIT")
print("=" * 60)

print(
    "Training:",
    len(X_train),
    f"({len(X_train) / len(X_dev) * 100:.1f}% of development)"
)

print(
    "Validation:",
    len(X_val),
    f"({len(X_val) / len(X_dev) * 100:.1f}% of development)"
)

print("\nTraining period:")

print(
    df["date"].iloc[0],
    "->",
    df["date"].iloc[validation_split_index - 1]
)

print("\nValidation period:")

print(
    df["date"].iloc[validation_split_index],
    "->",
    df["date"].iloc[split_index - 1]
)

print("\nTraining class distribution:")

print(
    y_train.value_counts().sort_index()
)

print("\nValidation class distribution:")

print(
    y_val.value_counts().sort_index()
)


# =========================================================
# 9. Scaling
# =========================================================
#
# IMPORTANT:
#
# The scaler is fitted ONLY on the training data.
#
# Validation and test data are transformed using the same
# scaler without fitting again.
# =========================================================

print("\n" + "=" * 60)
print("DATA SCALING")
print("=" * 60)


scaler = StandardScaler()


X_train_scaled = scaler.fit_transform(
    X_train.to_numpy(
        dtype=np.float32
    )
)

X_val_scaled = scaler.transform(
    X_val.to_numpy(
        dtype=np.float32
    )

)

X_test_scaled = scaler.transform(
    X_test.to_numpy(
        dtype=np.float32
    )
)


# =========================================================
# 10. Model factory
# =========================================================

def create_model():
    """
    Create the occupancy classification model.
    """

    model = keras.Sequential([
        layers.Input(
            shape=(len(FEATURES),)
        ),

        layers.Dense(
            16,
            activation="relu"
        ),

        layers.Dense(
            8,
            activation="relu"
        ),

        layers.Dense(
            1,
            activation="sigmoid"
        )
    ])

    model.compile(
        optimizer=keras.optimizers.Adam(
            learning_rate=0.001
        ),

        loss="binary_crossentropy",

        metrics=[
            "accuracy"
        ]
    )

    return model


# =========================================================
# 11. Train model
# =========================================================

print("\n" + "=" * 60)
print("MODEL TRAINING")
print("=" * 60)


model = create_model()


early_stopping = keras.callbacks.EarlyStopping(
    monitor="val_loss",
    patience=10,
    restore_best_weights=True,
)


history = model.fit(

    X_train_scaled,
    y_train,

    validation_data=(
        X_val_scaled,
        y_val
    ),

    epochs=100,

    batch_size=32,

    shuffle=False,

    callbacks=[
        early_stopping
    ],

    verbose=1
)


# =========================================================
# 12. Final test evaluation
# =========================================================
#
# The final 30% of the dataset is evaluated only here.
#
# It has never been used for:
#   - fitting the scaler
#   - training the model
#   - early stopping
# =========================================================

probabilities = model.predict(
    X_test_scaled,
    verbose=0
).flatten()


y_pred = (
    probabilities >= THRESHOLD
).astype(np.int32)


accuracy = accuracy_score(
    y_test,
    y_pred
)

precision = precision_score(
    y_test,
    y_pred,
    zero_division=0
)

recall = recall_score(
    y_test,
    y_pred,
    zero_division=0
)

f1 = f1_score(
    y_test,
    y_pred,
    zero_division=0
)


if len(np.unique(y_test)) == 2:

    roc_auc = roc_auc_score(
        y_test,
        probabilities
    )

else:

    roc_auc = np.nan


print("\n" + "=" * 60)
print("FINAL TEST RESULTS")
print("=" * 60)

print(
    f"Accuracy : {accuracy:.4f}"
)

print(
    f"Precision: {precision:.4f}"
)

print(
    f"Recall   : {recall:.4f}"
)

print(
    f"F1-score : {f1:.4f}"
)

print(
    f"ROC-AUC  : {roc_auc:.4f}"
)


print(
    "\nClassification report:"
)


print(
    classification_report(
        y_test,
        y_pred,

        target_names=[
            "Unoccupied",
            "Occupied"
        ],

        zero_division=0
    )
)


# =========================================================
# 13. Confusion matrix
# =========================================================

cm = confusion_matrix(
    y_test,
    y_pred
)


print(
    "Confusion matrix:"
)

print(cm)


plt.figure(
    figsize=(6, 5)
)

plt.imshow(cm)

plt.title(
    "Final Test Confusion Matrix"
)

plt.xlabel(
    "Predicted"
)

plt.ylabel(
    "Actual"
)

plt.xticks(
    [0, 1],
    [
        "Unoccupied",
        "Occupied"
    ]
)

plt.yticks(
    [0, 1],
    [
        "Unoccupied",
        "Occupied"
    ]
)


for i in range(
    cm.shape[0]
):

    for j in range(
        cm.shape[1]
    ):

        plt.text(
            j,
            i,
            cm[i, j],
            ha="center",
            va="center"
        )


plt.colorbar()

plt.tight_layout()

plt.savefig(
    "confusion_matrix.png",
    dpi=300,
    bbox_inches="tight"
)

plt.show()


# =========================================================
# 14. ROC curve
# =========================================================

if len(np.unique(y_test)) == 2:

    fpr, tpr, thresholds = roc_curve(
        y_test,
        probabilities
    )


    plt.figure(
        figsize=(8, 6)
    )


    plt.plot(
        fpr,
        tpr,
        label=f"ROC curve (AUC = {roc_auc:.4f})"
    )


    plt.plot(
        [0, 1],
        [0, 1],
        linestyle="--",
        label="Random chance"
    )


    plt.xlabel(
        "False Positive Rate"
    )

    plt.ylabel(
        "True Positive Rate"
    )

    plt.title(
        "Final Test ROC Curve"
    )

    plt.xlim(
        0,
        1
    )

    plt.ylim(
        0,
        1.05
    )

    plt.grid(
        True,
        linestyle="--",
        alpha=0.5
    )

    plt.legend()

    plt.tight_layout()

    plt.savefig(
        "roc_curve.png",
        dpi=300,
        bbox_inches="tight"
    )

    plt.show()


# =========================================================
# 15. Final metrics plot
# =========================================================

metrics = {
    "Accuracy": accuracy,
    "Precision": precision,
    "Recall": recall,
    "F1-score": f1,
    "ROC-AUC": roc_auc,
}


plt.figure(
    figsize=(10, 6)
)


bars = plt.bar(
    metrics.keys(),
    metrics.values()
)


plt.ylim(
    0,
    1.05
)

plt.ylabel(
    "Score"
)

plt.title(
    "Final Test Classification Metrics"
)

plt.grid(
    axis="y",
    linestyle="--",
    alpha=0.5
)


for bar, value in zip(
    bars,
    metrics.values()
):

    if not np.isnan(value):

        plt.text(
            bar.get_x()
            + bar.get_width() / 2,

            value + 0.02,

            f"{value:.4f}",

            ha="center",

            va="bottom"
        )


plt.tight_layout()

plt.savefig(
    "classification_metrics.png",
    dpi=300,
    bbox_inches="tight"
)

plt.show()


# =========================================================
# 16. Save final Keras model
# =========================================================

keras_path = (
    "occupancy_classifier.keras"
)


model.save(
    keras_path
)


print(
    "\nSaved Keras model:"
)


print(
    os.path.abspath(
        keras_path
    )
)


# =========================================================
# 17. Save scaler parameters
# =========================================================

print("\n" + "=" * 60)

print("SCALER parameters")

print("=" * 60)

for feature, mean, scale in zip(FEATURES, scaler.mean_, scaler.scale_):
    print(f"{feature:12s} -> Media: {mean:10.4f} | Dev. Std: {scale:10.4f}")

print("=" * 60)


# =========================================================
# 18. emlearn conversion
# =========================================================

cmodel = emlearn.convert(
    model,
    method="inline",
    return_type="classifier"
)


cmodel.save(
    name="occupancy_classifier",
    file="occupancy_classifier.h"
)


print(
    "Saved:",
    os.path.abspath(
        "occupancy_classifier.h"
    )
)


# =========================================================
# 19. Show emlearn header size
# =========================================================

header_size = os.path.getsize(
    "occupancy_classifier.h"
)


print(
    "Header size:",
    header_size,
    "bytes"
)


# =========================================================
# 20. Test one sample
# =========================================================

sample = X_test.iloc[
    0
].to_numpy(
    dtype=np.float32
)


sample_scaled = scaler.transform(
    sample.reshape(
        1,
        -1
    )
)


probability = model.predict(
    sample_scaled,
    verbose=0
)[0, 0]


prediction = int(
    probability >= THRESHOLD
)


print(
    "\nInput:"
)


for name, value in zip(
    FEATURES,
    sample
):

    print(
        f"  {name}: {value}"
    )


print(
    f"\nP(occupied): "
    f"{probability:.4f}"
)


print(
    "Prediction:",
    "OCCUPIED"
    if prediction
    else "UNOCCUPIED"
)


print(
    "Actual:",
    "OCCUPIED"
    if y_test.iloc[0] == 1
    else "UNOCCUPIED"
)
