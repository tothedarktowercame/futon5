use nalgebra::{linalg::Schur, SMatrix, SymmetricEigen};
use num_complex::Complex;
use serde_json::Value;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::io::{self, Read, Write};
use std::sync::atomic::{AtomicUsize, Ordering as AtomicOrdering};

#[derive(Debug, Clone)]
enum Bencode {
    Int(i64),
    Bytes(Vec<u8>),
    List(Vec<Bencode>),
    Dict(BTreeMap<Vec<u8>, Bencode>),
}

#[derive(Debug)]
enum ParseError {
    NeedMore,
    Invalid(String),
}

static PARSE_ERROR_COUNT: AtomicUsize = AtomicUsize::new(0);

fn parse_int(bytes: &[u8]) -> Result<i64, ParseError> {
    let s = std::str::from_utf8(bytes)
        .map_err(|_| ParseError::Invalid("invalid int utf8".to_string()))?;
    s.parse::<i64>()
        .map_err(|_| ParseError::Invalid("invalid int".to_string()))
}

fn parse_len(bytes: &[u8]) -> Result<usize, ParseError> {
    let s = std::str::from_utf8(bytes)
        .map_err(|_| ParseError::Invalid("invalid len utf8".to_string()))?;
    s.parse::<usize>()
        .map_err(|_| ParseError::Invalid("invalid len".to_string()))
}

fn parse_at(buf: &[u8], mut idx: usize) -> Result<(Bencode, usize), ParseError> {
    if idx >= buf.len() {
        return Err(ParseError::NeedMore);
    }
    match buf[idx] {
        b'i' => {
            idx += 1;
            let start = idx;
            while idx < buf.len() && buf[idx] != b'e' {
                idx += 1;
            }
            if idx >= buf.len() {
                return Err(ParseError::NeedMore);
            }
            let int_val = parse_int(&buf[start..idx])?;
            Ok((Bencode::Int(int_val), idx + 1))
        }
        b'l' => {
            idx += 1;
            let mut list = Vec::new();
            loop {
                if idx >= buf.len() {
                    return Err(ParseError::NeedMore);
                }
                if buf[idx] == b'e' {
                    return Ok((Bencode::List(list), idx + 1));
                }
                let (item, next) = parse_at(buf, idx)?;
                list.push(item);
                idx = next;
            }
        }
        b'd' => {
            idx += 1;
            let mut dict = BTreeMap::new();
            loop {
                if idx >= buf.len() {
                    return Err(ParseError::NeedMore);
                }
                if buf[idx] == b'e' {
                    return Ok((Bencode::Dict(dict), idx + 1));
                }
                let (key, next) = parse_at(buf, idx)?;
                let key_bytes = match key {
                    Bencode::Bytes(b) => b,
                    _ => {
                        return Err(ParseError::Invalid(
                            "dict key must be bytes".to_string(),
                        ))
                    }
                };
                let (val, next2) = parse_at(buf, next)?;
                dict.insert(key_bytes, val);
                idx = next2;
            }
        }
        b'0'..=b'9' => {
            let start = idx;
            while idx < buf.len() && buf[idx] != b':' {
                idx += 1;
            }
            if idx >= buf.len() {
                return Err(ParseError::NeedMore);
            }
            let len = parse_len(&buf[start..idx])?;
            idx += 1;
            if idx + len > buf.len() {
                return Err(ParseError::NeedMore);
            }
            let bytes = buf[idx..idx + len].to_vec();
            Ok((Bencode::Bytes(bytes), idx + len))
        }
        _ => Err(ParseError::Invalid("invalid bencode prefix".to_string())),
    }
}

fn encode_bytes(data: &[u8]) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(format!("{}:", data.len()).as_bytes());
    out.extend_from_slice(data);
    out
}

fn encode_bencode(val: &Bencode) -> Vec<u8> {
    match val {
        Bencode::Int(i) => format!("i{}e", i).into_bytes(),
        Bencode::Bytes(b) => encode_bytes(b),
        Bencode::List(list) => {
            let mut out = vec![b'l'];
            for item in list {
                out.extend_from_slice(&encode_bencode(item));
            }
            out.push(b'e');
            out
        }
        Bencode::Dict(dict) => {
            let mut out = vec![b'd'];
            for (k, v) in dict.iter() {
                out.extend_from_slice(&encode_bytes(k));
                out.extend_from_slice(&encode_bencode(v));
            }
            out.push(b'e');
            out
        }
    }
}

fn dict_get<'a>(dict: &'a BTreeMap<Vec<u8>, Bencode>, key: &str) -> Option<&'a Bencode> {
    dict.get(key.as_bytes())
}

fn bencode_str(val: &Bencode) -> Option<String> {
    if let Bencode::Bytes(bytes) = val {
        String::from_utf8(bytes.clone()).ok()
    } else {
        None
    }
}

fn bencode_int(val: &Bencode) -> Option<i64> {
    if let Bencode::Int(i) = val {
        Some(*i)
    } else {
        None
    }
}

fn json_number_to_f64(v: &Value) -> Option<f64> {
    match v {
        Value::Number(n) => n.as_f64().or_else(|| n.as_i64().map(|x| x as f64)),
        _ => None,
    }
}

fn build_matrix(input: &Value) -> Result<(SMatrix<f64, 6, 6>, bool), String> {
    let symmetric = input
        .get("symmetric")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);

    if let Some(rows) = input.get("rows") {
        let rows = rows
            .as_array()
            .ok_or_else(|| "rows must be a vector".to_string())?;
        if rows.len() != 6 {
            return Err("rows must have length 6".to_string());
        }
        let mut data = [0.0f64; 36];
        for (i, row) in rows.iter().enumerate() {
            let row = row
                .as_array()
                .ok_or_else(|| "row must be a vector".to_string())?;
            if row.len() != 6 {
                return Err("each row must have length 6".to_string());
            }
            for (j, val) in row.iter().enumerate() {
                let num = json_number_to_f64(val)
                    .ok_or_else(|| "row entries must be numbers".to_string())?;
                data[i * 6 + j] = num;
            }
        }
        Ok((SMatrix::from_row_slice(&data), symmetric))
    } else if let Some(data) = input.get("data") {
        let data = data
            .as_array()
            .ok_or_else(|| "data must be a vector".to_string())?;
        if data.len() != 36 {
            return Err("data must have length 36".to_string());
        }
        let mut arr = [0.0f64; 36];
        for (i, val) in data.iter().enumerate() {
            let num = json_number_to_f64(val)
                .ok_or_else(|| "data entries must be numbers".to_string())?;
            arr[i] = num;
        }
        Ok((SMatrix::from_row_slice(&arr), symmetric))
    } else {
        Err("expected :data (len 36) or :rows (6x6)".to_string())
    }
}

fn check_symmetric(m: &SMatrix<f64, 6, 6>, eps: f64) -> bool {
    for i in 0..6 {
        for j in (i + 1)..6 {
            if (m[(i, j)] - m[(j, i)]).abs() > eps {
                return false;
            }
        }
    }
    true
}

fn eigenvalues_for(matrix: SMatrix<f64, 6, 6>, symmetric: bool) -> Result<Value, String> {
    if symmetric {
        if !check_symmetric(&matrix, 1.0e-9) {
            return Err("matrix is not symmetric within epsilon".to_string());
        }
        let eigen = SymmetricEigen::new(matrix);
        let mut values: Vec<f64> = eigen.eigenvalues.iter().cloned().collect();
        values.sort_by(|a, b| a.partial_cmp(b).unwrap_or(Ordering::Equal));
        Ok(serde_json::json!({ "eigenvalues": values }))
    } else {
        // Bound Schur iterations so pathological matrices cannot run forever.
        let eps = 1.0e-12_f64;
        let max_niter = 256_usize;
        let schur = Schur::try_new(matrix, eps, max_niter)
            .ok_or_else(|| format!("schur decomposition failed to converge within {} iterations", max_niter))?;
        let complex_vals = schur.complex_eigenvalues();
        let mut values: Vec<Complex<f64>> = complex_vals.iter().cloned().collect();
        values.sort_by(|a, b| {
            let re = a.re.partial_cmp(&b.re).unwrap_or(Ordering::Equal);
            if re == Ordering::Equal {
                a.im.partial_cmp(&b.im).unwrap_or(Ordering::Equal)
            } else {
                re
            }
        });
        let pairs: Vec<[f64; 2]> = values.iter().map(|c| [c.re, c.im]).collect();
        Ok(serde_json::json!({ "eigenvalues": pairs }))
    }
}

fn response_map(id: Option<Bencode>, pairs: Vec<(&str, Bencode)>) -> Bencode {
    let mut dict = BTreeMap::new();
    if let Some(id) = id {
        dict.insert(b"id".to_vec(), id);
    }
    for (k, v) in pairs {
        dict.insert(k.as_bytes().to_vec(), v);
    }
    Bencode::Dict(dict)
}

fn handle_describe(id: Option<Bencode>, stdout: &mut dyn Write) -> io::Result<()> {
    let var = Bencode::Dict(BTreeMap::from([
        (b"name".to_vec(), Bencode::Bytes(b"eigenvalues".to_vec())),
        (
            b"doc".to_vec(),
            Bencode::Bytes(b"Compute eigenvalues for a 6x6 matrix.".to_vec()),
        ),
        (
            b"arglists".to_vec(),
            Bencode::Bytes(b"([m])".to_vec()),
        ),
    ]));

    let ns = Bencode::Dict(BTreeMap::from([
        (b"name".to_vec(), Bencode::Bytes(b"pod.eigs".to_vec())),
        (b"vars".to_vec(), Bencode::List(vec![var])),
    ]));

    let resp = response_map(
        id,
        vec![
            ("op", Bencode::Bytes(b"describe".to_vec())),
            ("format", Bencode::Bytes(b"json".to_vec())),
            ("namespaces", Bencode::List(vec![ns])),
        ],
    );

    let encoded = encode_bencode(&resp);
    stdout.write_all(&encoded)?;
    stdout.flush()?;
    Ok(())
}

fn write_error(id: Option<Bencode>, msg: &str, stdout: &mut dyn Write) -> io::Result<()> {
    let resp = response_map(
        id,
        vec![
            ("op", Bencode::Bytes(b"invoke".to_vec())),
            ("ex-message", Bencode::Bytes(msg.as_bytes().to_vec())),
            ("ex-type", Bencode::Bytes(b"Exception".to_vec())),
        ],
    );
    let encoded = encode_bencode(&resp);
    stdout.write_all(&encoded)?;
    stdout.flush()?;
    Ok(())
}

fn handle_invoke(dict: &BTreeMap<Vec<u8>, Bencode>, stdout: &mut dyn Write) -> io::Result<()> {
    let id = dict_get(dict, "id").cloned();
    let var = dict_get(dict, "var").and_then(bencode_str);

    let var = match var {
        Some(v) => v,
        None => return write_error(id, "missing var", stdout),
    };

    if var != "pod.eigs/eigenvalues" {
        return write_error(id, "unknown var", stdout);
    }

    let args = dict_get(dict, "args");
    let arg_bytes = match args {
        Some(Bencode::List(items)) if !items.is_empty() => match &items[0] {
            Bencode::Bytes(b) => Some(b.clone()),
            _ => None,
        },
        Some(Bencode::Bytes(b)) => Some(b.clone()),
        _ => None,
    };

    let arg_bytes = match arg_bytes {
        Some(b) => b,
        None => return write_error(id, "missing args", stdout),
    };

    let json_input: Value = match serde_json::from_slice(&arg_bytes) {
        Ok(v) => v,
        Err(_) => return write_error(id, "invalid json input", stdout),
    };

    let json_input = match json_input {
        Value::Array(mut items) if items.len() == 1 => items.remove(0),
        Value::Array(_) => return write_error(id, "expected single arg map", stdout),
        other => other,
    };

    let (matrix, symmetric) = match build_matrix(&json_input) {
        Ok(v) => v,
        Err(e) => return write_error(id, &e, stdout),
    };

    let output = match eigenvalues_for(matrix, symmetric) {
        Ok(v) => v,
        Err(e) => return write_error(id, &e, stdout),
    };

    let value = match serde_json::to_string(&output) {
        Ok(s) => s,
        Err(_) => return write_error(id, "failed to serialize output", stdout),
    };

    let resp = response_map(
        id,
        vec![
            ("op", Bencode::Bytes(b"invoke".to_vec())),
            ("value", Bencode::Bytes(value.as_bytes().to_vec())),
        ],
    );
    let encoded = encode_bencode(&resp);
    stdout.write_all(&encoded)?;
    stdout.flush()?;
    Ok(())
}

fn handle_message(msg: Bencode, stdout: &mut dyn Write) -> io::Result<()> {
    let dict = match msg {
        Bencode::Dict(d) => d,
        _ => return Ok(()),
    };
    let op = dict_get(&dict, "op").and_then(bencode_str).unwrap_or_default();
    let id = dict_get(&dict, "id").cloned();

    match op.as_str() {
        "describe" => handle_describe(id, stdout),
        "invoke" => handle_invoke(&dict, stdout),
        "shutdown" => Ok(()),
        _ => Ok(()),
    }
}

fn main() -> io::Result<()> {
    let mut stdin = io::stdin();
    let mut stdout = io::stdout();
    let mut buffer: Vec<u8> = Vec::new();
    let mut chunk = [0u8; 4096];

    loop {
        let n = stdin.read(&mut chunk)?;
        if n == 0 {
            break;
        }
        buffer.extend_from_slice(&chunk[..n]);

        loop {
            match parse_at(&buffer, 0) {
                Ok((msg, used)) => {
                    buffer.drain(0..used);
                    if let Bencode::Dict(ref dict) = msg {
                        if let Some(Bencode::Bytes(op)) = dict_get(dict, "op") {
                            if op == b"shutdown" {
                                return Ok(());
                            }
                        }
                    }
                    handle_message(msg, &mut stdout)?;
                }
                Err(ParseError::NeedMore) => break,
                Err(ParseError::Invalid(msg)) => {
                    // Recover by dropping one byte and retrying parse. Clearing the
                    // whole buffer can desynchronize request/response matching and
                    // leave the host waiting forever.
                    let n = PARSE_ERROR_COUNT.fetch_add(1, AtomicOrdering::Relaxed) + 1;
                    eprintln!(
                        "pod-eigs parse error #{}: {} (buffer-len={})",
                        n,
                        msg,
                        buffer.len()
                    );
                    if !buffer.is_empty() {
                        buffer.drain(0..1);
                        continue;
                    }
                    break;
                }
            }
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn close_enough(a: f64, b: f64) -> bool {
        (a - b).abs() < 1.0e-6
    }

    #[test]
    fn identity_matrix() {
        let mut data = [0.0f64; 36];
        for i in 0..6 {
            data[i * 6 + i] = 1.0;
        }
        let matrix = SMatrix::from_row_slice(&data);
        let out = eigenvalues_for(matrix, true).unwrap();
        let vals = out.get("eigenvalues").unwrap().as_array().unwrap();
        assert_eq!(vals.len(), 6);
        for v in vals {
            assert!(close_enough(v.as_f64().unwrap(), 1.0));
        }
    }

    #[test]
    fn zero_matrix() {
        let data = [0.0f64; 36];
        let matrix = SMatrix::from_row_slice(&data);
        let out = eigenvalues_for(matrix, true).unwrap();
        let vals = out.get("eigenvalues").unwrap().as_array().unwrap();
        assert_eq!(vals.len(), 6);
        for v in vals {
            assert!(close_enough(v.as_f64().unwrap(), 0.0));
        }
    }

    #[test]
    fn symmetric_block() {
        let mut data = [0.0f64; 36];
        data[0] = 2.0;
        data[1] = 1.0;
        data[6] = 1.0;
        data[7] = 2.0;
        let matrix = SMatrix::from_row_slice(&data);
        let out = eigenvalues_for(matrix, true).unwrap();
        let vals = out.get("eigenvalues").unwrap().as_array().unwrap();
        let mut nums: Vec<f64> = vals.iter().map(|v| v.as_f64().unwrap()).collect();
        nums.sort_by(|a, b| a.partial_cmp(b).unwrap_or(Ordering::Equal));
        assert_eq!(nums.len(), 6);
        assert!(close_enough(nums[5], 3.0));
        assert!(close_enough(nums[4], 1.0));
    }
}
