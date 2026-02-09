#!/usr/bin/env python3
"""
Script to generate SQL migration data from service-providers.json
"""

import json
import uuid
from typing import Dict, List, Any

def generate_uuid() -> str:
    """Generate a UUID string"""
    return str(uuid.uuid4())

def escape_sql_string(value: str) -> str:
    """Escape SQL string values"""
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"

def generate_service_provider_insert(service: Dict[str, Any], service_id: str) -> str:
    """Generate INSERT statement for service_provider table"""
    # Use EN alias as display_name, fallback to KO if EN doesn't exist
    display_name = service.get("aliasNames", {}).get("EN", 
                        service.get("aliasNames", {}).get("KO", "Unknown"))
    
    logo_suffix = service.get("logoDevSuffix", None)
    subscription_url = service.get("subscriptionPageUrl", None)
    website_url = service.get("websiteUrl", None)
    
    return f"""INSERT INTO public.service_provider (id, display_name, logo_dev_suffix, payment_cycle, subscription_page_url, website_url) VALUES ({escape_sql_string(service_id)}, {escape_sql_string(display_name)}, {escape_sql_string(logo_suffix)}, NULL, {escape_sql_string(subscription_url)}, {escape_sql_string(website_url)});"""

def generate_email_source_insert(service: Dict[str, Any], service_id: str, email: str) -> str:
    """Generate INSERT statement for email_source table"""
    email_id = generate_uuid()
    return f"""INSERT INTO public.email_source (id, analyzed_message_ids, event_rules, is_active, target_address, service_provider_id) VALUES ({escape_sql_string(email_id)}, '[]', '[]', true, {escape_sql_string(email)}, {escape_sql_string(service_id)});"""

def generate_alias_names_insert(service_id: str, alias_names: Dict[str, str]) -> List[str]:
    """Generate INSERT statements for service_provider_alias_names table"""
    statements = []
    for locale, name in alias_names.items():
        if name:  # Skip empty names
            statements.append(
                f"""INSERT INTO public.service_provider_alias_names (service_provider_id, alias_name, locale_key) VALUES ({escape_sql_string(service_id)}, {escape_sql_string(name)}, {escape_sql_string(locale)});"""
            )
    return statements

def generate_sql_from_json(json_file_path: str, output_file_path: str):
    """Main function to generate SQL from JSON"""
    
    # Read JSON file
    with open(json_file_path, 'r', encoding='utf-8') as f:
        services = json.load(f)
    
    sql_statements = []
    
    # Add header
    sql_statements.append("""--
-- Generated SQL from service-providers.json
--""")

    sql_statements.append("""--
-- Data for Name: service_provider; Type: TABLE DATA; Schema: public; Owner: -
--
""")
    
    # Generate service provider and related data
    for service in services:
        service_id = generate_uuid()
        
        # Generate service provider insert
        sql_statements.append(generate_service_provider_insert(service, service_id))
        
        # Generate email source inserts for each email address
        email_addresses = service.get("emailAddresses", [])
        for email in email_addresses:
            sql_statements.append(generate_email_source_insert(service, service_id, email))
        
        # Generate alias names inserts
        alias_names = service.get("aliasNames", {})
        alias_statements = generate_alias_names_insert(service_id, alias_names)
        sql_statements.extend(alias_statements)
    
    # Add email_source section header
    sql_statements.insert(
        len([s for s in sql_statements if s.startswith("INSERT INTO public.service_provider")]) + 3,
        """--
-- Data for Name: email_source; Type: TABLE DATA; Schema: public; Owner: -
--
"""
    )
    
    # Add alias names section header
    service_provider_count = len([s for s in sql_statements if s.startswith("INSERT INTO public.service_provider")])
    email_source_count = len([s for s in sql_statements if s.startswith("INSERT INTO public.email_source")])
    
    alias_header_index = service_provider_count + email_source_count + 6
    sql_statements.insert(
        alias_header_index,
        """--
-- Data for Name: service_provider_alias_names; Type: TABLE DATA; Schema: public; Owner: -
--
"""
    )
    
    # Write SQL to file
    with open(output_file_path, 'w', encoding='utf-8') as f:
        for statement in sql_statements:
            f.write(statement + '\n')
    
    print(f"Generated {len(sql_statements)} SQL statements")
    print(f"Service providers: {len(services)}")
    print(f"Output written to: {output_file_path}")

if __name__ == "__main__":
    # File paths
    json_file = "src/main/resources/static/service-providers.json"
    output_file = "src/main/resources/db/migration/V2__init_data_generated.sql"
    
    generate_sql_from_json(json_file, output_file)
