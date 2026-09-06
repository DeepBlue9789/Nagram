import os
import sys

OLD_SIGN = b"3A0F57FE06485D0B90D0ACD990E3A30328E3988D"
NEW_SIGN = b"BE9440DA79F181160E273DF810D716443A8993E1"

assert len(OLD_SIGN) == len(NEW_SIGN), "Signatures must have identical byte length!"

def patch_file(filepath):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return False
    with open(filepath, "rb") as f:
        data = f.read()
    
    count = data.count(OLD_SIGN)
    if count == 0:
        if NEW_SIGN in data:
            print(f"Already patched: {filepath}")
            return True
        print(f"Warning: Signature not found in {filepath}")
        return False
    
    patched_data = data.replace(OLD_SIGN, NEW_SIGN)
    with open(filepath, "wb") as f:
        f.write(patched_data)
    print(f"Successfully patched {count} occurrence(s) in {filepath}")
    return True

if __name__ == "__main__":
    libs_dir = r"d:\Antigravity projects\Nagram\TMessagesProj\src\main\libs"
    for root, dirs, files in os.walk(libs_dir):
        for file in files:
            if file == "libtmessages.49.so":
                patch_file(os.path.join(root, file))
