import pandas as pd
import os

columns = ["label", "class"]
class_map_df = pd.read_csv(
    "OID/csv_folder/class-descriptions-boxable.csv", header=None, names=columns
)
annotation_df = pd.read_csv("OID/csv_folder/validation-annotations-bbox.csv")

label_list = class_map_df.loc[
    class_map_df["class"].isin(["Bird", "Car", "Dog", "Flower", "Person"])
]["label"].unique()

data_df = pd.DataFrame(
    columns=[
        "type",
        "path",
        "class",
        "XMin",
        "YMin",
        "anon0",
        "anon1",
        "XMax",
        "YMax",
        "anon2",
        "anon3",
    ]
)

i = 0
for class_name in os.listdir("OID/Dataset/validation"):
    filenames = os.listdir(f"OID/Dataset/validation/{class_name}")
    # print(len(filenames))
    # print(class_name, filenames[0])
    for filename in filenames[:180]:
        image_id = os.path.splitext(filename)[0]
        df = annotation_df.loc[
            annotation_df["ImageID"].isin([image_id])
            & annotation_df["LabelName"].isin(label_list)
        ].filter(
            [
                "ImageID",
                "LabelName",
                "XMin",
                "XMax",
                "YMin",
                "YMax",
            ]
        )
        if len(df) != 1:
            continue
        data_df.loc[i] = [
            "TRAIN",
            f"OID/Dataset/validation/{class_name}/{filename}",
            class_name,
            df.iloc[0]["XMin"],
            df.iloc[0]["YMin"],
            None,
            None,
            df.iloc[0]["XMax"],
            df.iloc[0]["YMax"],
            None,
            None,
        ]
        i += 1
    for filename in filenames[180:220]:
        image_id = os.path.splitext(filename)[0]
        df = annotation_df.loc[
            annotation_df["ImageID"].isin([image_id])
            & annotation_df["LabelName"].isin(label_list)
        ].filter(
            [
                "ImageID",
                "LabelName",
                "XMin",
                "XMax",
                "YMin",
                "YMax",
            ]
        )
        if len(df) != 1:
            continue
        data_df.loc[i] = [
            "VALIDATE",
            f"OID/Dataset/validation/{class_name}/{filename}",
            class_name,
            df.iloc[0]["XMin"],
            df.iloc[0]["YMin"],
            None,
            None,
            df.iloc[0]["XMax"],
            df.iloc[0]["YMax"],
            None,
            None,
        ]
        i += 1
    for filename in filenames[220:260]:
        image_id = os.path.splitext(filename)[0]
        df = annotation_df.loc[
            annotation_df["ImageID"].isin([image_id])
            & annotation_df["LabelName"].isin(label_list)
        ].filter(
            [
                "ImageID",
                "LabelName",
                "XMin",
                "XMax",
                "YMin",
                "YMax",
            ]
        )
        if len(df) != 1:
            continue
        data_df.loc[i] = [
            "TEST",
            f"OID/Dataset/validation/{class_name}/{filename}",
            class_name,
            df.iloc[0]["XMin"],
            df.iloc[0]["YMin"],
            None,
            None,
            df.iloc[0]["XMax"],
            df.iloc[0]["YMax"],
            None,
            None,
        ]
        i += 1

data_df.to_csv("oid_dataset.csv", header=None, index=False)
