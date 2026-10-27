mod combiner;
mod cue;
mod utils;
mod vcd;

use anyhow::{Context, Result};
use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use std::path::{Path, PathBuf};
use combiner::BinCombiner;
use cue::CueSheet;
use utils::detect_game_id;
use vcd::VcdConverter;

fn clean_game_name(name: &str) -> String {
    let cleaned: String = name
        .chars()
        .map(|ch| {
            if ch.is_alphanumeric() || ch == ' ' || ch == '-' || ch == '_' || ch == '.' {
                ch
            } else {
                '_'
            }
        })
        .collect();
    let cleaned = cleaned.trim().trim_matches('.');
    if cleaned.is_empty() {
        String::from("game")
    } else {
        cleaned.to_string()
    }
}

fn convert_cue(input: &Path, output_dir: &Path, gap_plus: bool, gap_minus: bool) -> Result<PathBuf> {
    if gap_plus && gap_minus {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            "gap_plus and gap_minus cannot be enabled together",
        )
        .into());
    }

    if input.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("cue")) != Some(true) {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidInput,
            "Input must be a CUE file",
        )
        .into());
    }

    let mut cue_sheet = CueSheet::parse(input).context("Could not parse the CUE sheet")?;
    let cue_dir = input.parent().unwrap_or_else(|| Path::new("."));
    cue_sheet.load_file_sizes(cue_dir).context("Could not locate all files referenced by CUE")?;
    cue_sheet.validate_mode2().context("Unsupported track layout")?;

    if cue_sheet.files.is_empty() {
        return Err(anyhow::Error::msg("The CUE sheet does not reference any data files"));
    }

    let first_bin = cue_dir.join(&cue_sheet.files[0].filename);
    let game_id = detect_game_id(&first_bin).context("Could not scan the first track for a game ID")?;

    let raw_name = input
        .file_stem()
        .and_then(|value| value.to_str())
        .ok_or_else(|| anyhow::Error::msg("Invalid CUE filename"))?;
    let game_name = clean_game_name(raw_name);

    std::fs::create_dir_all(output_dir).context("Could not create the output directory")?;
    let combined_bin = output_dir.join(format!("{}_combined.bin", game_name));
    let temp_vcd = output_dir.join(format!("{}.VCD", game_name));

    let combine_result = BinCombiner::combine(&mut cue_sheet, cue_dir, &combined_bin)
        .context("Could not combine the BIN tracks");
    if let Err(error) = combine_result {
        let _ = std::fs::remove_file(&combined_bin);
        return Err(error);
    }

    let converter = VcdConverter::new(gap_plus, gap_minus);
    let convert_result = converter.convert_to_vcd(&combined_bin, &temp_vcd, &cue_sheet)
        .context("Could not write the VCD file");
    let _ = std::fs::remove_file(&combined_bin);
    if let Err(error) = convert_result {
        let _ = std::fs::remove_file(&temp_vcd);
        return Err(error);
    }

    if let Some(id) = game_id {
        let final_path = output_dir.join(format!("{}.{}.VCD", id, game_name));
        if final_path.exists() {
            std::fs::remove_file(&final_path).context("Could not replace an older VCD")?;
        }
        std::fs::rename(&temp_vcd, &final_path).context("Could not finalize the VCD filename")?;
        Ok(final_path)
    } else {
        Ok(temp_vcd)
    }
}

#[no_mangle]
pub extern "system" fn Java_org_seryo_psxvcd_MainActivity_nativeConvert(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    input_cue: JString<'_>,
    output_dir: JString<'_>,
    gap_plus: jboolean,
    gap_minus: jboolean,
) -> jstring {
    let result = (|| -> Result<String> {
        let input: String = env.get_string(&input_cue)?.into();
        let output: String = env.get_string(&output_dir)?.into();
        let path = convert_cue(Path::new(&input), Path::new(&output), gap_plus != 0, gap_minus != 0)?;
        Ok(path.to_string_lossy().into_owned())
    })();

    let response = match result {
        Ok(path) => format!("OK\n{}", path),
        Err(error) => format!("ERROR\n{:#}", error),
    };

    match env.new_string(response) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
